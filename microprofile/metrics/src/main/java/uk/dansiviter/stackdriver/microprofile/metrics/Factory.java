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

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.google.api.Distribution;
import com.google.api.LabelDescriptor;
import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.MetricDescriptor.ValueType;
import com.google.cloud.MonitoredResource;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.monitoring.v3.TimeSeries.Builder;
import com.google.protobuf.Timestamp;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
@ApplicationScoped
public class Factory {
	private static final ThreadLocal<MetricDescriptor.Builder> METRIC_DESC_BUILDER =
			ThreadLocal.withInitial(MetricDescriptor::newBuilder);
	private static final ThreadLocal<LabelDescriptor.Builder> LABEL_BUILDER =
			ThreadLocal.withInitial(LabelDescriptor::newBuilder);
	private static final ThreadLocal<TimeSeries.Builder> TIMESERIES_BUILDER =
			ThreadLocal.withInitial(TimeSeries::newBuilder);
	private static final ThreadLocal<Point.Builder> POINT_BUILDER =
			ThreadLocal.withInitial(Point::newBuilder);
	private static final ThreadLocal<TimeInterval.Builder> INTERVAL_BUILDER =
			ThreadLocal.withInitial(TimeInterval::newBuilder);
	private static final ThreadLocal<Timestamp.Builder> TIMESTAMP_BUILDER =
			ThreadLocal.withInitial(Timestamp::newBuilder);
	private static final ThreadLocal<TypedValue.Builder> TYPED_VALUE_BUILDER =
			ThreadLocal.withInitial(TypedValue::newBuilder);
	private static final ThreadLocal<Distribution.Builder> DISTRIBUTION_BUILDER =
			ThreadLocal.withInitial(Distribution::newBuilder);

	@Inject
	@ConfigProperty(name = "stackdriver.prefix", defaultValue = "custom.googleapis.com/microprofile/")
	private String prefix;

	/**
	 *
	 * @param start
	 * @param end
	 * @return
	 */
	TimeInterval toInterval(ZonedDateTime start, ZonedDateTime end) {
		final TimeInterval.Builder b = INTERVAL_BUILDER.get().clear();
		b.setStartTime(toTimestamp(start));
		b.setEndTime(toTimestamp(end));
		return b.build();
	}

	/**
	 *
	 * @param t
	 * @return
	 */
	private static Timestamp toTimestamp(ZonedDateTime t) {
		return TIMESTAMP_BUILDER.get().clear().setSeconds(t.toEpochSecond()).setNanos(t.getNano()).build();
	}

	/**
	 *
	 * @param registry
	 * @param id
	 * @param metric
	 * @return
	 */
	MetricDescriptor toDescriptor(MetricRegistry registry, MetricID id, Snapshot snapshot) {
		final String name = id.getName();
		final String metricType = prefix.concat(name);
		final Metadata metadata = registry.getMetadata().get(name);
		final MetricDescriptor.Builder descriptor = METRIC_DESC_BUILDER.get().clear().setType(metricType)
				.setMetricKind(getMetricKind(metadata.getTypeRaw()))
				.setName(name)
				.setDisplayName(metadata.getDisplayName());
		getValueType(snapshot).ifPresent(descriptor::setValueType);
		metadata.getDescription().ifPresent(descriptor::setDescription);
		metadata.getUnit().ifPresent(u -> descriptor.setUnit(convertUnit(u)));
		id.getTagsAsList().forEach(t -> descriptor.addLabels(
			LABEL_BUILDER.get().clear().setKey(t.getTagName())
					.setValueType(LabelDescriptor.ValueType.STRING)));
		return descriptor.build();
	}

	/**
	 *
	 * @param metric
	 * @return
	 */
	private static Optional<ValueType> getValueType(Snapshot snapshot) {
		if (snapshot instanceof GaugeSnapshot) {
			final GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
			final Object value = gaugeSnapshot.value();
			if (value instanceof Double || value instanceof Float) {
				return Optional.of(ValueType.DOUBLE);
			}
			if (value instanceof Short || value instanceof Integer || value instanceof Long) {
				return Optional.of(ValueType.INT64);
			}
		} else if (snapshot instanceof ConcurrentGaugeSnapshot) {
			return Optional.of(ValueType.INT64);
		} else if (snapshot instanceof CounterSnapshot) {
			return Optional.of(ValueType.INT64);
		} else if (snapshot instanceof TimerSnapshot) {
			return Optional.of(ValueType.DISTRIBUTION);
		}
		return Optional.empty();
	}

	/**
	 *
	 * @param type
	 * @return
	 */
	private static MetricKind getMetricKind(MetricType type) {
		if (type == null) {
			return MetricKind.METRIC_KIND_UNSPECIFIED;
		}
		switch (type) {
			case COUNTER:
				return MetricKind.CUMULATIVE;
			case GAUGE:
			case CONCURRENT_GAUGE:
				return MetricKind.GAUGE;
			case TIMER:
				return MetricKind.GAUGE;
			default:
				return MetricKind.UNRECOGNIZED;
		}
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
	 * @param metric
	 * @return
	 */
	static Optional<Snapshot> toSnapshot(Metric metric) {
		final Snapshot snapshot;
		if (metric instanceof Gauge) {
			snapshot = new GaugeSnapshot((Gauge<?>) metric);
		} else if (metric instanceof ConcurrentGauge) {
			snapshot = new ConcurrentGaugeSnapshot((ConcurrentGauge) metric);
		} else if (metric instanceof Counter) {
			snapshot = new CounterSnapshot((Counter) metric);
		} else if (metric instanceof Timer) {
			snapshot = new TimerSnapshot((Timer) metric);
		}
		else {
			snapshot = null;
		}
		return Optional.ofNullable(snapshot);
	}


	// --- Inner Classes ---

	/**
	 *
	 */
	interface Snapshot {
		default TimeSeries.Builder timeseries(
				MetricID id,
				MetricDescriptor descriptor,
				MonitoredResource monitoredResource,
				TimeInterval interval)
		{
			final Map<String, String> metricLabels = new HashMap<>();
			id.getTags().forEach(metricLabels::put);
			return TIMESERIES_BUILDER.get().clear()
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
	static class GaugeSnapshot implements Snapshot {
		private final Object value;

		private GaugeSnapshot(Gauge<?> gauge) {
			this.value = gauge.getValue();
		}

		Object value() {
			return this.value;
		}

		private TypedValue value(MetricDescriptor descriptor) {
			final TypedValue.Builder typedValue = TYPED_VALUE_BUILDER.get().clear();
			switch (descriptor.getValueType()) {
				case DOUBLE:
					typedValue.setDoubleValue(((Number) value()).doubleValue());
					break;
				case INT64:
					typedValue.setInt64Value(((Number) value()).longValue());
					break;
				default:
					throw new IllegalArgumentException("Unknown type! [" + descriptor.getValueType() + "]");
				}
			return typedValue.build();
		}

		@Override
		public TimeSeries.Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource,
				TimeInterval interval) {
			final Point point = POINT_BUILDER.get().clear()
					.setInterval(INTERVAL_BUILDER.get().clear().setEndTime(interval.getEndTime()).build())
					.setValue(value(descriptor)).build();
			return Snapshot.super.timeseries(id, descriptor, monitoredResource, interval)
				.addPoints(point);
		}
	}

	/**
	 *
	 */
	static class ConcurrentGaugeSnapshot implements Snapshot {
		private long value;

		private ConcurrentGaugeSnapshot(ConcurrentGauge gauge) {
			this.value = gauge.getCount();
		}

		@Override
		public TimeSeries.Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource,
				TimeInterval interval) {
			final Point point = POINT_BUILDER.get().clear()
					.setInterval(INTERVAL_BUILDER.get().clear().setEndTime(interval.getEndTime()).build())
					.setValue(TYPED_VALUE_BUILDER.get().clear().setInt64Value(this.value).build()).build();
			return Snapshot.super.timeseries(id, descriptor, monitoredResource, interval)
				.addPoints(point);
		}
	}

	/**
	 *
	 */
	static class CounterSnapshot implements Snapshot {
		private final long value;

		private CounterSnapshot(Counter counter) {
			this.value = counter.getCount();
		}

		@Override
		public TimeSeries.Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource,
				TimeInterval interval) {
			final Point point = POINT_BUILDER.get().clear()
					.setInterval(interval)
					.setValue(TYPED_VALUE_BUILDER.get().clear().setInt64Value(this.value).build()).build();
			return Snapshot.super.timeseries(id, descriptor, monitoredResource, interval).addPoints(point);
		}
	}

	static class TimerSnapshot implements Snapshot {
		private final double mean;
		private final long count;
		private final long[] values;

		private TimerSnapshot(Timer timer) {
			this.mean = timer.getMeanRate();
			this.count = timer.getCount();
			this.values = timer.getSnapshot().getValues();
		}

		@Override
		public Builder timeseries(MetricID id, MetricDescriptor descriptor, MonitoredResource monitoredResource, TimeInterval interval) {
			final Distribution.Builder distribution = DISTRIBUTION_BUILDER.get().clear()
					.setMean(this.mean)
					.setCount(this.count);
			final TimeSeries.Builder builder = Snapshot.super.timeseries(id, descriptor, monitoredResource, interval);

			for (long value : this.values) {
				final Point point = POINT_BUILDER.get().clear()
				.setInterval(interval)
				.setValue(TYPED_VALUE_BUILDER.get().clear().setDistributionValue(distribution).setInt64Value(value).build()).build();
				builder.addPoints(point);
			}
			return builder;
		}
	}
}
