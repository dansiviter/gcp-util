/*
 * Copyright 2019-2020 Daniel Siviter
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
package uk.dansiviter.gcp.monitoring.metrics;

import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static uk.dansiviter.gcp.monitoring.ResourceType.Label.PROJECT_ID;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.google.api.MetricDescriptor;
import com.google.cloud.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.Timestamp;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import uk.dansiviter.gcp.monitoring.GaxUtil;
import uk.dansiviter.gcp.monitoring.ResourceType;
import uk.dansiviter.gcp.monitoring.metrics.Factory.Context;
import uk.dansiviter.gcp.monitoring.metrics.Factory.Snapshot;

/**
 * Bridges between the Microprofile Metrics API and Cloud Monitoring to collect and push metrics.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Nov 2019]
 */
@ApplicationScoped
public class CloudMonitoringExporter {
	private final Map<MetricID, MetricDescriptor> descriptors = new ConcurrentHashMap<>();
	protected final Logger log;

	@Inject
	@ConfigProperty(name = "stackdriver.samplingRate", defaultValue = "STANDARD")
	private SamplingRate samplingRate;
	@Inject
	private ScheduledExecutorService executor;
	// Annoyingly you can't get the type directly from the registry
	@Inject @Any
	private Instance<MetricRegistry> registries;
	@Inject
	private MonitoredResource monitoredResource;
	@Inject
	private Config config;

	private ZonedDateTime startDateTime;
	private ZonedDateTime previousIntervalEndTime;

	private ProjectName projectName;
	private MetricServiceClient client;
	private ScheduledFuture<?> future;

	public CloudMonitoringExporter() {
		this.log = Logger.getLogger(getClass().getName());
	}

	/**
	 *
	 * @param init simply here to force initialisation.
	 * @throws IOException
	 */
	public void init(@Observes @Initialized(ApplicationScoped.class) Object init) throws IOException {
		this.startDateTime = ZonedDateTime.now(UTC);
		this.projectName = ProjectName.of(ResourceType.get(this.monitoredResource, PROJECT_ID).orElseThrow());
		final MetricServiceSettings.Builder builder = MetricServiceSettings.newBuilder();
		this.client = MetricServiceClient.create(builder.build());
		this.future = this.executor.scheduleAtFixedRate(this::run, 0, samplingRate.duration.getSeconds(), SECONDS);
	}

	/**
	 *
	 */
	private void run() {
		final ZonedDateTime intervalStartTime = this.previousIntervalEndTime == null ? this.startDateTime : this.previousIntervalEndTime;
		final ZonedDateTime intervalEndTime = ZonedDateTime.now(UTC);
		this.log.log(Level.FINE, "Starting metrics collection... [start={0},end={1}]",
				new Object[] { intervalStartTime, intervalEndTime });
		try {
			final Timestamp startTimestamp = Factory.toTimestamp(this.startDateTime);
			final TimeInterval interval = Factory.toInterval(intervalEndTime, intervalEndTime);

			// collect as fast as possible. storage can take a little longer
			final Map<MetricID, Snapshot> snapshots = new ConcurrentHashMap<>();
			for (MetricRegistry.Type type : MetricRegistry.Type.values()) {
				final MetricRegistry registry = this.registries.select(Factory.registryType(type)).get();
				registry.getMetrics().forEach((k, v) -> collect(snapshots, k, v));
			}
			final Context ctx = new Context(this.config, monitoredResource, startTimestamp, interval);
			final List<TimeSeries> timeSeries = new ArrayList<>();

			for (MetricRegistry.Type type : MetricRegistry.Type.values()) {
				final MetricRegistry registry = this.registries.select(Factory.registryType(type)).get();
				registry.getMetrics().forEach((k, v) -> {
					timeSeries(ctx, registry, type, snapshots, k).ifPresent(ts -> add(timeSeries, ts));
				});
			}

			// limit to 200: https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.timeSeries/create
			for (List<TimeSeries> chunk : partition(timeSeries, 200)) {
				this.log.log(Level.FINE, "Persisting time series. [size={0}]", chunk.size());
				final CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
						.setName(this.projectName.toString())
						.addAllTimeSeries(chunk)
						.build();
				this.client.createTimeSeries(request);
			}
			this.previousIntervalEndTime = intervalEndTime;
		} catch (RuntimeException e) {
			this.log.log(Level.WARNING, "Unable to collect metrics!", e);
		}
	}

	private static void add(List<TimeSeries> timeSeries, TimeSeries ts) {
		if (ts.getPointsCount() != 1) {
			throw new IllegalStateException("Naugty! " + ts);
		}
		timeSeries.add(ts);
	}

	/**
	 *
	 * @param registry
	 * @param snapshots
	 * @param id
	 * @param metric
	 */
	private void collect(Map<MetricID, Snapshot> snapshots, MetricID id, Metric metric) {
		try {
			Factory.toSnapshot(metric).ifPresent(s -> snapshots.put(id, s));
		} catch (RuntimeException e) {
			this.log.log(Level.WARNING, "Unable to snapshot!", e);
		}
	}

	@PreDestroy
	public void destroy() {
		if (this.future != null) {
			this.future.cancel(false);
		}
		GaxUtil.close(this.client);
	}


	// --- Static Methods ---

	/**
	 *
	 * @param registry
	 * @param type
	 * @param snapshots
	 * @param startTime
	 * @param interval
	 * @param id
	 * @param metric
	 * @return
	 */
	private Optional<TimeSeries> timeSeries(
			Context ctx,
			MetricRegistry registry,
			MetricRegistry.Type type,
			Map<MetricID, Snapshot> snapshots,
			MetricID id)
	{
		final Snapshot snapshot = snapshots.get(id);
		if (snapshot == null) {
			return Optional.empty(); // we either couldn't snapshot or don't know how
		}

		// on first run create metric view data as we have no other way of knowing if it's a Double or Int64
		final MetricDescriptor descriptor = this.descriptors.computeIfAbsent(id, k -> {
			final MetricDescriptor created = Factory.toDescriptor(this.config, registry, type, id, snapshot);
			return this.client.createMetricDescriptor(this.projectName, created);
		});

		return Optional.of(snapshot.timeseries(ctx, id, descriptor).build());
	}

	/**
	 *
	 * @param in
	 * @param chunk
	 * @return
	 */
	private static Collection<List<TimeSeries>> partition(List<TimeSeries> in, int chunk) {
		final AtomicInteger counter = new AtomicInteger();
		return in.stream().collect(groupingBy(it -> counter.getAndIncrement() / chunk)).values();
	}


	// --- Inner Classes ---

	/**
	 * @see https://cloud.google.com/blog/products/management-tools/cloud-monitoring-metrics-get-10-second-resolution
	 */
	public enum SamplingRate {
		STANDARD(Duration.parse("PT1M")),
		HIGH_RESOLUTION(Duration.parse("PT10S"));

		private final Duration duration;

		SamplingRate(Duration duration) {
			this.duration = duration;
		}
	}
}
