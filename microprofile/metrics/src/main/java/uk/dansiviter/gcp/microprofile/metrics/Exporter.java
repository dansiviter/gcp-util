/*
 * Copyright 2019-2021 Daniel Siviter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.dansiviter.gcp.microprofile.metrics;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.BASE;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.VENDOR;
import static uk.dansiviter.gcp.ResourceType.Label.PROJECT_ID;
import static uk.dansiviter.gcp.microprofile.metrics.Factory.toInterval;
import static uk.dansiviter.gcp.microprofile.metrics.Factory.toTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.google.api.MetricDescriptor;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeSeries;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import uk.dansiviter.gcp.GaxUtil;
import uk.dansiviter.gcp.MonitoredResourceProvider;
import uk.dansiviter.gcp.microprofile.metrics.Factory.Context;
import uk.dansiviter.gcp.microprofile.metrics.Factory.Snapshot;

/**
 * Bridges between the Microprofile Metrics API and Cloud Monitoring to collect and push metrics.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Nov 2019]
 */
@ApplicationScoped
public class Exporter {
	private final Map<MetricID, MetricDescriptor> descriptors = new ConcurrentHashMap<>();
	private final Supplier<MonitoredResource> resourceSupplier;

	@Inject
	private Log log;
	@Inject
	@ConfigProperty(name = "cloudMonitoring.enabled", defaultValue = "true")
	private boolean enabled;
	@Inject
	@ConfigProperty(name = "cloudMonitoring.samplingRate", defaultValue = "STANDARD")
	private SamplingRate samplingRate;
	@Inject
	private ScheduledExecutorService executor;
	@Inject @RegistryType(type = Type.BASE)
	private MetricRegistry baseRegistry;
	@Inject @RegistryType(type = Type.VENDOR)
	private MetricRegistry vendorRegistry;
	@Inject @RegistryType(type = Type.APPLICATION)
	private MetricRegistry appRegistry;
	@Inject
	private Config config;
	@Inject @Filter
	private Instance<Predicate<MetricID>> filters;
	@Inject
	private Instance<MetricServiceClient> client;

	private MonitoredResource resource;

	private Instant startInstant;
	private Instant previousInstant;

	private ProjectName projectName;
	private ScheduledFuture<?> future;

	/**
	 * Default constructor.
	 */
	public Exporter() {
		this(MonitoredResourceProvider::monitoredResource);
	}

	Exporter(Supplier<MonitoredResource> resourceSupplier) {
		this.resourceSupplier = resourceSupplier;
	}

	/**
	 * @param init simply here to force initialisation.
	 */
	public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
		if (!this.enabled || this.client == null) {
			return;  // no client available so don't initialise
		}
		this.startInstant = Instant.now();
		this.resource = this.resourceSupplier.get();
		var projectId = PROJECT_ID.get(this.resource);
		if (!projectId.isPresent()) {
			log.projectIdNotFound();
			return;
		}
		this.projectName = ProjectName.of(projectId.orElseThrow());
		// Ensure small diff. in start > end time
		// https://cloud.google.com/monitoring/api/ref_v3/rest/v3/TimeInterval
		this.future = this.executor.scheduleAtFixedRate(this::flush, 10, samplingRate.duration.getSeconds(), SECONDS);
	}

	// visible for testing
	void flush() {
		try {
			doFlush(this.client.get());
		} catch (RuntimeException e) {
			this.log.collectionFail(e);
		}
	}

	private void doFlush(MetricServiceClient client) {
		var start = this.previousInstant == null ? this.startInstant : this.previousInstant;
		var end = Instant.now();
		this.log.startCollection(start, end);
		var interval = toInterval(start, end);

		// collect snapshots quickly
		var snapshots = new ConcurrentHashMap<MetricID, Snapshot>();
		this.baseRegistry.getMetrics().forEach((k, v) -> collect(snapshots, k, v));
		this.vendorRegistry.getMetrics().forEach((k, v) -> collect(snapshots, k, v));
		this.appRegistry.getMetrics().forEach((k, v) -> collect(snapshots, k, v));

		// convert to time-series
		var ctx = new Context(this.config, this.resource, toTimestamp(this.startInstant), interval);
		var timeSeries = new ArrayList<TimeSeries>();
		convert(client, ctx, this.baseRegistry, BASE, snapshots, timeSeries);
		convert(client, ctx, this.vendorRegistry, VENDOR, snapshots, timeSeries);
		convert(client, ctx, this.appRegistry, APPLICATION, snapshots, timeSeries);

		// persist
		// limit to 200: https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.timeSeries/create
		for (var chunk : partition(timeSeries, 200)) {
			this.log.persist(chunk.size());
			var request = CreateTimeSeriesRequest.newBuilder()
					.setName(this.projectName.toString())
					.addAllTimeSeries(chunk)
					.build();
			client.createTimeSeries(request);
		}
		this.previousInstant = end;
	}

	private void convert(
		MetricServiceClient client,
		Context ctx,
		MetricRegistry registry,
		Type type,
		Map<MetricID, Snapshot> snapshots,
		List<TimeSeries> timeSeries)
	{
		registry.getMetrics().forEach((k, v) ->
			timeSeries(client, ctx, registry, type, snapshots, k).ifPresent(ts -> add(timeSeries, ts))
		);
	}

	private static void add(List<TimeSeries> timeSeries, TimeSeries ts) {
		if (ts.getPointsCount() != 1) {
			throw new IllegalStateException(format("Must contain exactly one! [size=%d]", ts.getPointsCount()));
		}
		timeSeries.add(ts);
	}

	private void collect(Map<MetricID, Snapshot> snapshots, MetricID id, Metric metric) {
		for (var p : this.filters) {
			if (!p.test(id)) {
				return;
			}
		}

		try {
			Factory.toSnapshot(metric).ifPresent(s -> snapshots.put(id, s));
		} catch (RuntimeException e) {
			this.log.snapshotFail(e);
		}
	}

	/**
	 * Destroy this exporter.
	 */
	@PreDestroy
	public void destroy() {
		if (this.future != null) {
			this.future.cancel(false);
		}
		if (this.previousInstant != null) {
			var client = this.client.get();
			doFlush(client);
			GaxUtil.close(client);
		}
	}

	private Optional<TimeSeries> timeSeries(
		MetricServiceClient client,
		Context ctx,
		MetricRegistry registry,
		MetricRegistry.Type type,
		Map<MetricID, Snapshot> snapshots,
		MetricID id)
	{
		var snapshot = snapshots.get(id);
		if (snapshot == null) {
			return Optional.empty();  // we either couldn't snapshot or don't know how
		}

		var descriptor = this.descriptors.computeIfAbsent(id, k -> metricDescriptor(client, registry, type, snapshot, id));

		return Optional.of(snapshot.timeseries(ctx, id, descriptor).build());
	}

	private MetricDescriptor metricDescriptor(
		MetricServiceClient client,
		MetricRegistry registry,
		MetricRegistry.Type type,
		Snapshot snapshot,
		MetricID id)
	{
		// to save churn for no reason (especially in audit log), first load the existing metric and only create if
		// different
		var name = Factory.toDescriptorName(this.resource, type, id);
		MetricDescriptor found;
		try {
			found = client.getMetricDescriptor(name);
		} catch (NotFoundException e) {
			found = null;
		}
		var created = Factory.toDescriptor(this.resource, this.config, registry, type, id, snapshot);
		if (!like(found, created)) {
			return client.createMetricDescriptor(this.projectName, created);
		}
		return found;
	}


	// --- Static Methods ---


	private static Collection<List<TimeSeries>> partition(List<TimeSeries> in, int chunk) {
		var counter = new AtomicInteger();
		return in.stream().collect(groupingBy(it -> counter.getAndIncrement() / chunk)).values();
	}

	static boolean like(MetricDescriptor base, MetricDescriptor test) {
		if (base == null) {
			return false;
		}
		// verifies if 'base' contains [is like] everything in 'test'
		return base.getName().equals(test.getName()) &&
			base.getType().equals(test.getType()) &&
			base.getLabelsList().containsAll(test.getLabelsList()) &&
			base.getMetricKind() == test.getMetricKind() &&
			base.getValueType() == test.getValueType() &&
			base.getUnit().equals(test.getUnit()) &&
			base.getDescription().equals(test.getDescription()) &&
			base.getDisplayName().equals(test.getDisplayName()) &&
			base.getLaunchStage() == test.getLaunchStage() &&
			base.getMonitoredResourceTypesList().containsAll(test.getMonitoredResourceTypesList());
	}


	// --- Inner Classes ---

	/**
	 * @see <a href="https://cloud.google.com/blog/products/management-tools/cloud-monitoring-metrics-get-10-second-resolution">High-resolution user-defined metrics</a>
	 */
	public enum SamplingRate {
		/** Standard sampling rate */
		STANDARD(Duration.parse("PT1M")),
		/** High resolution sampling rate */
		HIGH_RESOLUTION(Duration.parse("PT10S"));

		private final Duration duration;

		SamplingRate(Duration duration) {
			this.duration = duration;
		}
	}
}
