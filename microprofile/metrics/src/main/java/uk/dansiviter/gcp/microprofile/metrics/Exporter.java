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

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.BASE;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.VENDOR;
import static uk.dansiviter.gcp.ResourceType.Label.PROJECT_ID;
import static uk.dansiviter.gcp.microprofile.metrics.Factory.toInterval;
import static uk.dansiviter.gcp.microprofile.metrics.Factory.toTimestamp;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.google.api.MetricDescriptor;
import com.google.cloud.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeSeries;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import uk.dansiviter.gcp.AtomicInit;
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
	private final MonitoredResource resource = MonitoredResourceProvider.monitoredResource();

	@Inject
	private Logger log;
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

	private Instant startInstant;
	private Instant previousInstant;

	private ProjectName projectName;
	private AtomicInit<MetricServiceClient> client = new AtomicInit<>(this::createClient);
	private ScheduledFuture<?> future;

	/**
	 * @param init simply here to force initialisation.
	 */
	public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
		this.startInstant = Instant.now();
		var projectId = PROJECT_ID.get(this.resource);
		if (!projectId.isPresent()) {
			log.projectIdNotFound();
			return;
		}
		this.projectName = ProjectName.of(PROJECT_ID.get(this.resource).orElseThrow());
		// Ensure small diff. in start > end time
		// https://cloud.google.com/monitoring/api/ref_v3/rest/v3/TimeInterval
		this.future = this.executor.scheduleAtFixedRate(this::flush, 10, samplingRate.duration.getSeconds(), SECONDS);
	}

	private void flush() {
		try {
			doFlush();
		} catch (RuntimeException e) {
			this.log.collectionFail(e);
		}
	}

	private void doFlush() {
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
		convert(ctx, this.baseRegistry, BASE, snapshots, timeSeries);
		convert(ctx, this.vendorRegistry, VENDOR, snapshots, timeSeries);
		convert(ctx, this.appRegistry, APPLICATION, snapshots, timeSeries);

		// persist
		// limit to 200: https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.timeSeries/create
		for (var chunk : partition(timeSeries, 200)) {
			this.log.persist(chunk.size());
			var request = CreateTimeSeriesRequest.newBuilder()
					.setName(this.projectName.toString())
					.addAllTimeSeries(chunk)
					.build();
			this.client.get().createTimeSeries(request);
		}
		this.previousInstant = end.plusMillis(1);  // prevent overlap
	}

	private void convert(
		Context ctx,
		MetricRegistry registry,
		Type type,
		Map<MetricID, Snapshot> snapshots,
		List<TimeSeries> timeSeries)
	{
		registry.getMetrics().forEach((k, v) ->
			timeSeries(ctx, registry, type, snapshots, k).ifPresent(ts -> add(timeSeries, ts))
		);
	}

	private static void add(List<TimeSeries> timeSeries, TimeSeries ts) {
		if (ts.getPointsCount() != 1) {
			throw new IllegalStateException("Naughty! " + ts);
		}
		timeSeries.add(ts);
	}

	private void collect(Map<MetricID, Snapshot> snapshots, MetricID id, Metric metric) {
		for (Predicate<MetricID> p : this.filters) {
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

	private MetricServiceClient createClient() {
		try {
			var builder = MetricServiceSettings.newBuilder();
			return MetricServiceClient.create(builder.build());
		} catch (IOException e) {
			throw new IllegalStateException(e);
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
			flush();
		}
		this.client.closeIfInitialised(GaxUtil::close);
	}

	private Optional<TimeSeries> timeSeries(
			Context ctx,
			MetricRegistry registry,
			MetricRegistry.Type type,
			Map<MetricID, Snapshot> snapshots,
			MetricID id)
	{
		var snapshot = snapshots.get(id);
		if (snapshot == null) {
			return Optional.empty(); // we either couldn't snapshot or don't know how
		}

		// on first run create metric view data as we have no other way of knowing if it's a Double or Int64
		var descriptor = this.descriptors.computeIfAbsent(id, k -> {
			var created = Factory.toDescriptor(this.resource, this.config, registry, type, id, snapshot);
			return this.client.get().createMetricDescriptor(this.projectName, created);
		});

		return Optional.of(snapshot.timeseries(ctx, id, descriptor).build());
	}

	// --- Static Methods ---


	private static Collection<List<TimeSeries>> partition(@Nonnull List<TimeSeries> in, int chunk) {
		var counter = new AtomicInteger();
		return in.stream().collect(groupingBy(it -> counter.getAndIncrement() / chunk)).values();
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


	public static void main(String... args) {
		var count = 500;
		var buckets = new long[12];

		var mean = BigDecimal.valueOf(count).divide(BigDecimal.valueOf(buckets.length), MathContext.DECIMAL128);
		var sum = 0L;

		for (int i = 0; i < buckets.length; i++) {
			var current = mean.multiply(BigDecimal.valueOf(i + 1));
			buckets[i] = current.setScale(0, RoundingMode.FLOOR).longValueExact();
			if (i > 0) {
//				buckets[i] -= sum;
			}
			sum += buckets[i];
		}

		System.out.printf("Buckets: %s\n", Arrays.toString(buckets));
		if (count != sum) {
			throw new IllegalStateException("Count and sum not equal! [c=" + count + ",s=" + sum + "]");
		}
		System.out.printf("Sum: %d\n", sum);
	}

	public static void primitives() {
		var count = 239487293847293874L;
		var buckets = new long[12];

		var mean = ((double) count) / buckets.length;
		var sum = 0L;
		for (int i = 0; i < buckets.length; i++) {
			var current = mean * (double) (i + 1);
			var v = current - sum;
			buckets[i] = Math.round(Math.floor(v));
			sum += buckets[i];
		}

		System.out.printf("Prim. Buckets: %s\n", Arrays.toString(buckets));
		if (count != sum) {
			throw new IllegalStateException("Count and sum not equal! [c=" + count + ",s=" + sum + "]");
		}
		System.out.printf("Prim. Sum: %d\n", sum);
	}
}
