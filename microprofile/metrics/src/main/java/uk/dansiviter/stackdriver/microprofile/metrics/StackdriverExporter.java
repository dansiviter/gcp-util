/*
 * Copyright 2019 Daniel Siviter
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
package uk.dansiviter.stackdriver.microprofile.metrics;

import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static uk.dansiviter.stackdriver.ResourceType.Label.PROJECT_ID;

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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import uk.dansiviter.stackdriver.ResourceType;
import uk.dansiviter.stackdriver.microprofile.metrics.Factory.Snapshot;

/**
 * TODO Histogram and Meter
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Nov 2019]
 */
@ApplicationScoped
public class StackdriverExporter {
	private final Map<MetricID, MetricDescriptor> descriptors = new ConcurrentHashMap<>();
	protected final Logger log;

	// matches the default poll duration:
	// https://cloud.google.com/monitoring/api/metrics_agent#agent-jvm
	@Inject
	@ConfigProperty(name = "stackdriver.pollDuration", defaultValue = "PT1M")
	private Duration pollDuration;
	@Inject
	private ScheduledExecutorService executor;
	@Inject @Any
	private Instance<MetricRegistry> registries;
	@Inject
	private MonitoredResource monitoredResource;
	@Inject
	private Factory factory;

	private ZonedDateTime startDateTime = ZonedDateTime.now(UTC);
	private ProjectName projectName;
	private MetricServiceClient client;
	private ScheduledFuture<?> future;

	public StackdriverExporter() {
		this.log = Logger.getLogger(getClass().getName());
	}

	/**
	 *
	 * @param init simply here to force initialisation.
	 * @throws IOException
	 */
	public void init(@Observes @Initialized(ApplicationScoped.class) Object init) throws IOException {
		this.projectName = ProjectName.of(ResourceType.get(this.monitoredResource, PROJECT_ID).get());
		final MetricServiceSettings.Builder builder = MetricServiceSettings.newBuilder();
		this.client = MetricServiceClient.create(builder.build());

		this.future = executor.scheduleAtFixedRate(this::run, 0, pollDuration.getSeconds(), SECONDS);
	}

	/**
	 *
	 */
	private void run() {
		final ZonedDateTime dateTime = ZonedDateTime.now(UTC);
		this.log.log(Level.INFO, "Starting metrics collection... [start={0},end={0}]",
				new Object[] { startDateTime, dateTime });
		try {
			final TimeInterval interval = this.factory.toInterval(startDateTime, dateTime);

			// collect as fast as possible. storage can take a little longer
			final Map<MetricID, Snapshot> snapshots = new ConcurrentHashMap<>();
			this.registries.forEach(r -> {
				r.getMetrics().forEach((k, v) -> collect(r, snapshots, k, v));
			});

			final List<TimeSeries> timeSeries = new ArrayList<>();
			this.registries.forEach(r -> {
				r.getMetrics().forEach((k, v) -> {
					timeSeries(r, snapshots, interval, k, v).ifPresent(timeSeries::add);
				});
			});

			// limit to 200: https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.timeSeries/create
			for (List<TimeSeries> chunk : partition(timeSeries, 200)) {
				this.log.log(Level.FINE, "Persisting time series. [size={0}]", chunk.size());
				final CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
						.setName(this.projectName.toString())
						.addAllTimeSeries(chunk)
						.build();
				this.client.createTimeSeries(request);
			}
			this.startDateTime = dateTime;
		} catch (RuntimeException e) {
			this.log.log(Level.WARNING, "Unable to collect metrics!", e);
		}
	}

	/**
	 *
	 * @param registry
	 * @param snapshots
	 * @param id
	 * @param metric
	 */
	private void collect(MetricRegistry registry, Map<MetricID, Snapshot> snapshots, MetricID id, Metric metric) {
		try {
			Factory.snapshot(metric).ifPresent(s -> snapshots.put(id, s));
		} catch (RuntimeException e) {
			this.log.log(Level.WARNING, "Unable to snapshot!", e);
		}
	}

	/**
	 *
	 */
	public void destroy() {
		if (this.future != null) {
			this.future.cancel(false);
		}
		if (this.client != null) {
			this.client.close();
		}
	}


	// --- Static Methods ---

	/**
	 *
	 * @param registry
	 * @param snapshots
	 * @param interval
	 * @param id
	 * @param metric
	 * @return
	 */
	private Optional<TimeSeries> timeSeries(
		MetricRegistry registry,
		Map<MetricID, Snapshot> snapshots,
		TimeInterval interval, MetricID id, Metric metric)
	{
		final Snapshot snapshot = snapshots.get(id);
		if (snapshot == null) {
			return Optional.empty(); // we either couldn't snapshot or don't know how
		}

		// on first run create metric view data as we have no other way of knowing if it's a Double or Int64
		final MetricDescriptor descriptor = this.descriptors.computeIfAbsent(id, k -> {
			final MetricDescriptor created = this.factory.toDescriptor(registry, id, metric);
			this.client.createMetricDescriptor(this.projectName, created);
			return created;
		});

		return Optional.of(snapshot.timeseries(id, descriptor, monitoredResource, interval).build());
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
}
