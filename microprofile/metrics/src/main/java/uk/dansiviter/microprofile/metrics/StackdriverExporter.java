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
package uk.dansiviter.microprofile.metrics;

import static java.util.stream.Collectors.groupingBy;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import com.google.api.LabelDescriptor;
import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.MetricDescriptor.ValueType;
import com.google.cloud.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.CreateMetricDescriptorRequest;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TimeSeries.Builder;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;

import uk.dansiviter.stackdriver.ResourceType;
import uk.dansiviter.stackdriver.ResourceType.Label;

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

	@Inject
	@ConfigProperty(name = "stackdriver.prefix", defaultValue = "custom.googleapis.com/microprofile/")
	private String prefix;
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

	private ZonedDateTime startDateTime = ZonedDateTime.now(ZoneOffset.UTC);
	private String projectName;
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
		this.projectName = ProjectName.format(ResourceType.get(this.monitoredResource, Label.PROJECT_ID).get());
		final MetricServiceSettings.Builder builder = MetricServiceSettings.newBuilder();
		this.client = MetricServiceClient.create(builder.build());

		this.future = executor.scheduleAtFixedRate(this::run, 0, pollDuration.getSeconds(), TimeUnit.SECONDS);
	}

	/**
	 *
	 */
	private void run() {
		final ZonedDateTime dateTime = ZonedDateTime.now(ZoneOffset.UTC);
		this.log.log(Level.INFO, "Starting metrics collection... [start={0},end={0}]",
				new Object[] { startDateTime, dateTime });
		try {
			final TimeInterval interval = TimeInterval.newBuilder()
					.setStartTime(Timestamp.newBuilder()
							.setSeconds(startDateTime.toEpochSecond())
							.setNanos(startDateTime.getNano()))
					.setEndTime(Timestamp.newBuilder()
							.setSeconds(dateTime.toEpochSecond())
							.setNanos(dateTime.getNano())
							.build())
					.build();

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
						.setName(projectId())
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
			if (metric instanceof Gauge) {
				snapshots.put(id, new GaugeSnapshot((Gauge<?>) metric));
			} else if (metric instanceof ConcurrentGauge) {
				snapshots.put(id, new ConcurrentGaugeSnapshot((ConcurrentGauge) metric));
			} else if (metric instanceof Counter) {
				snapshots.put(id, new CounterSnapshot((Counter) metric));
			}
		} catch (RuntimeException e) {
			this.log.log(Level.WARNING, "Unable to snapshot!", e);
		}
	}

	/**
	 *
	 * @return
	 */
	private String projectId() {
		return this.projectName;
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
		final MetricDescriptor descriptor = this.descriptors.computeIfAbsent(
			id, k -> createDescriptor(registry, id, metric));

		return Optional.of(snapshot.timeseries(id, descriptor, monitoredResource, interval).build());
	}

	/**
	 *
	 * @param registry
	 * @param id
	 * @param metric
	 * @return
	 */
	private MetricDescriptor createDescriptor(MetricRegistry registry, MetricID id, Metric metric) {
		final String metricType = prefix.concat(id.getName());
		final Metadata metadata = registry.getMetadata().get(id.getName());
		final MetricDescriptor.Builder descriptor = MetricDescriptor.newBuilder().setType(metricType)
				.setMetricKind(getMetricKind(metric))
				.setValueType(getValueType(metric))
				.setName(metadata.getName())
				.setDisplayName(metadata.getDisplayName());
		metadata.getDescription().ifPresent(descriptor::setDescription);
		metadata.getUnit().ifPresent(u -> descriptor.setUnit(convertUnit(u)));
		id.getTagsAsList().forEach(t -> descriptor.addLabels(
			LabelDescriptor.newBuilder().setKey(t.getTagName())
			.setValueType(LabelDescriptor.ValueType.STRING)));

		final CreateMetricDescriptorRequest request = CreateMetricDescriptorRequest.newBuilder()
				.setName(projectId().toString()).setMetricDescriptor(descriptor.build()).build();

		return this.client.createMetricDescriptor(request);
	}

	/**
	 *
	 * @param metric
	 * @return
	 */
	private static ValueType getValueType(Metric metric) {
		if (metric instanceof Gauge) {
			final Gauge<?> gauge = (Gauge<?>) metric;
			final Object value = gauge.getValue();
			if (value instanceof Double || value instanceof Float) {
				return ValueType.DOUBLE;
			}
			if (value instanceof Short || value instanceof Integer || value instanceof Long) {
				return ValueType.INT64;
			}
		} else if (metric instanceof ConcurrentGauge) {
			return ValueType.INT64;
		} else if (metric instanceof Counter) {
			return ValueType.INT64;
		}
		throw new IllegalStateException("Unknown type! [" + metric + "]");
	}

	/**
	 *
	 * @param metric
	 * @return
	 */
	private static MetricKind getMetricKind(Metric metric) {
		if (metric instanceof Gauge || metric instanceof ConcurrentGauge) {
			return MetricKind.GAUGE;
		} else if (metric instanceof Counter) {
			return MetricKind.CUMULATIVE;
		}
		throw new IllegalStateException("Unknown type! [" + metric + "]");
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

	/**
	 *
	 * @param in
	 * @return
	 */
	private static String convertUnit(String in) {
		// Basic unit: bit, By, s, min, h, d
		// Prefixes: l, M, G, T, P, E, Z, Y, m, u, n, p, f, a, z, y, Ki, Mi, Gi, Ti
		in = in.toLowerCase();

		if (in.endsWith(MetricUnits.BITS)) {
			return convertPrefix(in, MetricUnits.BITS, "bit");
		} else if (in.endsWith(MetricUnits.BYTES)) {
			return convertPrefix(in, MetricUnits.BYTES, "By");
		} else if (in.endsWith(MetricUnits.SECONDS)) {
			return convertPrefix(in, MetricUnits.SECONDS, "s");
		} else if (in.equals(MetricUnits.MINUTES)) {
			return "min";
		} else if (in.equals(MetricUnits.HOURS)) {
			return "h";
		} else if (in.equals(MetricUnits.DAYS)) {
			return "d";
		} else if (in.equals(MetricUnits.PERCENT)) {
			return "%";
		} else if (in.equals(MetricUnits.PER_SECOND)) {
			return "1/s";
		}
		return in;
	}

	/**
	 *
	 * @param original
	 * @param oldUnit
	 * @param newUnit
	 * @return
	 * @see MetricUnits
	 */
	private static String convertPrefix(String original, String oldUnit, String newUnit) {
		if (original.equals(oldUnit)) {
			return newUnit;
		}
		final String prefix = original.substring(0, oldUnit.length() - 1);
		switch (prefix) {
			case "kilo":
			return "k".concat(newUnit);
			case "mega":
			return "M".concat(newUnit);
			case "giga":
			return "G".concat(newUnit);

			case "kibi":
			return "Ki".concat(newUnit);
			case "mebi":
			return "Mi".concat(newUnit);
			case "gibi":
			return "GI".concat(newUnit);

			default:
			return newUnit;
		}
	}

	/**
	 *
	 */
	private interface Snapshot {
		default TimeSeries.Builder timeseries(
				MetricID id,
				MetricDescriptor descriptor,
				MonitoredResource monitoredResource,
				TimeInterval interval)
		{
			final Map<String, String> metricLabels = new HashMap<>();
			id.getTags().forEach(metricLabels::put);
			return TimeSeries.newBuilder()
				.setMetric(com.google.api.Metric.newBuilder().setType(descriptor.getType()).putAllLabels(metricLabels)
						.build())
				.setResource(monitoredResource.toPb())
				.setMetricKind(descriptor.getMetricKind())
				.setValueType(descriptor.getValueType());
		}
	}

	 /**
	  *
	  */
	private class GaugeSnapshot implements Snapshot {
		final Object value;
		private GaugeSnapshot(Gauge<?> gauge) {
			value = gauge.getValue();
		}

		private TypedValue value(MetricDescriptor descriptor) {
			final TypedValue.Builder typedValue = TypedValue.newBuilder();
			switch (descriptor.getValueType()) {
				case DOUBLE:
					typedValue.setDoubleValue(((Number) this.value).doubleValue());
					break;
				case INT64:
					typedValue.setInt64Value(((Number) this.value).longValue());
					break;
				default:
					throw new IllegalArgumentException("Unknown type! [" + descriptor.getValueType() + "]");
				}
			return typedValue.build();
		}

		@Override
		public Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource,
				TimeInterval interval) {
			final Point point = Point.newBuilder()
					.setInterval(TimeInterval.newBuilder(interval).clearStartTime().build())
					.setValue(value(descriptor)).build();
			return Snapshot.super.timeseries(id, descriptor, monitoredResource, interval)
				.addPoints(point);
		}
	}

	/**
	 *
	 */
	private class ConcurrentGaugeSnapshot implements Snapshot {
		private long value;

		private ConcurrentGaugeSnapshot(ConcurrentGauge gauge) {
			this.value = gauge.getCount();
		}

		@Override
		public Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource,
				TimeInterval interval) {
			final Point point = Point.newBuilder()
					.setInterval(TimeInterval.newBuilder(interval).clearStartTime().build())
					.setValue(TypedValue.newBuilder().setInt64Value(this.value).build()).build();
			return Snapshot.super.timeseries(id, descriptor, monitoredResource, interval)
				.addPoints(point);
		}
	}

	/**
	 *
	 */
	private class CounterSnapshot implements Snapshot {
		final long value;
		private CounterSnapshot(Counter counter) {
			this.value = counter.getCount();
		}

		@Override
		public Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource,
				TimeInterval interval) {
			final Point point = Point.newBuilder()
					.setInterval(interval)
					.setValue(TypedValue.newBuilder().setInt64Value(this.value).build()).build();
			return Snapshot.super.timeseries(id, descriptor, monitoredResource, interval).addPoints(point);
		}
	}
}
