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

import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;
import static uk.dansiviter.gcp.Util.threadLocal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.google.api.Distribution;
import com.google.api.Distribution.BucketOptions;
import com.google.api.Distribution.BucketOptions.Exponential;
import com.google.api.LabelDescriptor;
import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.MetricDescriptor.ValueType;
import com.google.cloud.MonitoredResource;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TimeSeries.Builder;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metered;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Sampling;
import org.eclipse.microprofile.metrics.Tag;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public enum Factory { ;
	private static final ThreadLocal<MetricDescriptor.Builder> METRIC_DESC_BUILDER =
			threadLocal(MetricDescriptor::newBuilder, MetricDescriptor.Builder::clear);
	private static final ThreadLocal<LabelDescriptor.Builder> LABEL_BUILDER =
			threadLocal(LabelDescriptor::newBuilder, LabelDescriptor.Builder::clear);
	private static final ThreadLocal<TimeSeries.Builder> TIMESERIES_BUILDER =
			threadLocal(TimeSeries::newBuilder, TimeSeries.Builder::clear);
	private static final ThreadLocal<Point.Builder> POINT_BUILDER =
			threadLocal(Point::newBuilder, Point.Builder::clear);
	private static final ThreadLocal<TimeInterval.Builder> INTERVAL_BUILDER =
			threadLocal(TimeInterval::newBuilder, TimeInterval.Builder::clear);
	private static final ThreadLocal<Timestamp.Builder> TIMESTAMP_BUILDER =
			threadLocal(Timestamp::newBuilder, Timestamp.Builder::clear);
	private static final ThreadLocal<TypedValue.Builder> TYPED_VALUE_BUILDER =
			threadLocal(TypedValue::newBuilder, TypedValue.Builder::clear);
	private static final ThreadLocal<Distribution.Builder> DISTRIBUTION_BUILDER =
			threadLocal(Distribution::newBuilder, Distribution.Builder::clear);

	/**
	 * Create a new time interval.
	 *
	 * @param start the start time.
	 * @param end the end time.
	 * @return the new instance.
	 */
	public static TimeInterval toInterval(@Nonnull Instant start, @Nonnull Instant end) {
		final TimeInterval.Builder b = INTERVAL_BUILDER.get();
		b.setStartTime(toTimestamp(start));
		b.setEndTime(toTimestamp(end));
		return b.build();
	}

	/**
	 * Creates a new timestamp instant.
	 *
	 * @param t the input datetime.
	 * @return the new instance.
	 */
	static Timestamp toTimestamp(@Nonnull Instant i) {
		return TIMESTAMP_BUILDER.get().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
	}

	/**
	 * Creates a new descriptor instance.
	 *
	 * @param config the configuration.
	 * @param registry the registry.
	 * @param type the type.
	 * @param id the metric ID.
	 * @param snapshot the metrics snapshot.
	 * @return the created descriptor.
	 */
	public static MetricDescriptor toDescriptor(
		@Nonnull Config config,
		@Nonnull MetricRegistry registry,
		@Nonnull Type type,
		@Nonnull MetricID id,
		@Nonnull Snapshot snapshot)
	{
		final String name = id.getName();
		final String metricType = format("custom.googleapis.com/microprofile/%s/%s", type.getName(), name);
		final Metadata metadata = registry.getMetadata().get(name);
		final MetricDescriptor.Builder descriptor = METRIC_DESC_BUILDER.get().setType(metricType)
				.setMetricKind(getMetricKind(metadata.getTypeRaw())).setName(name)
				.setDisplayName(metadata.getDisplayName());
		getValueType(snapshot).ifPresent(descriptor::setValueType);
		metadata.getDescription().ifPresent(descriptor::setDescription);
		metadata.getUnit().ifPresentOrElse(
				u -> descriptor.setUnit(convertUnit(u)),
				() -> descriptor.setUnit("1"));
		id.getTags().forEach((k, v) -> descriptor.addLabels(labelDescriptor(config, k, v)));
		return descriptor.build();
	}

	/**
	 *
	 * @param config the configuration.
	 * @param key the key.
	 * @param value the value.
	 * @return the builder instance.
	 */
	private static LabelDescriptor.Builder labelDescriptor(
		@Nonnull Config config,
		@Nonnull String key,
		@Nonnull String value)
	{
		var builder = LABEL_BUILDER.get().setKey(key).setValueType(getValueType(value));
		config.labelDescription(key).ifPresent(builder::setDescription);
		return builder;
	}

	/**
	 *
	 * @param value the value to test.
	 * @return the type of value.
	 */
	private static LabelDescriptor.ValueType getValueType(@Nonnull String value) {
		try {
			Long.parseLong(value);
			return LabelDescriptor.ValueType.INT64;
		} catch (NumberFormatException e) {
			// do nothing!
		}
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
			return LabelDescriptor.ValueType.BOOL;
		}
		return LabelDescriptor.ValueType.STRING;
	}

	/**
	 *
	 * @param snapshot the metrics snapshot.
	 * @return the value type.
	 */
	private static Optional<ValueType> getValueType(Snapshot snapshot) {
		if (snapshot instanceof GaugeSnapshot) {
			final GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
			final Object value = gaugeSnapshot.value();
			if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
				return Optional.of(ValueType.DOUBLE);
			} else if (value instanceof Short || value instanceof Integer || value instanceof Long
					|| value instanceof BigInteger) {
				return Optional.of(ValueType.INT64);
			} else {
				throw new IllegalArgumentException("Unknown type! [" + value + "]");
			}
		} else if (snapshot instanceof ConcurrentGaugeSnapshot || snapshot instanceof CounterSnapshot
				|| snapshot instanceof MeteredSnapshot) {
			return Optional.of(ValueType.INT64);
		} else if (snapshot instanceof SamplingSnapshot) {
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
		// DELTA type is kinda useless!
		// https://cloud.google.com/monitoring/api/v3/metrics-details#metric-kinds
		switch (type) {
		case COUNTER:
			return MetricKind.CUMULATIVE;
		case GAUGE:
		case CONCURRENT_GAUGE:
		case TIMER:
		case METERED:
		case HISTOGRAM:
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

		if (MetricUnits.NONE.equals(in)) {
			return "1";
		} else if (in.endsWith(MetricUnits.BITS)) {
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
	private static String convertPrefix(
		@Nonnull String original,
		@Nonnull String oldUnit,
		@Nonnull String newUnit)
	{
		if (original.equals(oldUnit)) {
			return newUnit;
		}
		final String prefix = original.substring(0, original.length() - oldUnit.length());
		switch (prefix) {
		case "nano":
			return "n".concat(newUnit);
		case "micro":
			return "u".concat(newUnit);
		case "milli":
			return "m".concat(newUnit);

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
	 * Create new snapshot instance for metric.
	 *
	 * @param metric the metric to test.
	 * @return the snapshot.
	 */
	static Optional<Snapshot> toSnapshot(Metric metric) {
		final Snapshot snapshot;
		if (metric instanceof Gauge) {
			snapshot = new GaugeSnapshot((Gauge<?>) metric);
		} else if (metric instanceof ConcurrentGauge) {
			snapshot = new ConcurrentGaugeSnapshot((ConcurrentGauge) metric);
		} else if (metric instanceof Counter) {
			snapshot = new CounterSnapshot((Counter) metric);
		} else if (metric instanceof Sampling) {
			snapshot = new SamplingSnapshot((Sampling) metric);
		} else if (metric instanceof Metered) {
			snapshot = new MeteredSnapshot((Metered) metric);
		} else {
			snapshot = null;
		}
		return Optional.ofNullable(snapshot);
	}

	static void buckets(BucketOptions options, org.eclipse.microprofile.metrics.Snapshot snapshot,
			BucketConverter converter, Distribution.Builder distribution) {
		final List<Bucket> buckets;
		if (options.hasExponentialBuckets()) {
			buckets = exponentialBuckets(options);
		} else if (options.hasExplicitBuckets()) {
			buckets = explicitBuckets(options);
		} else if (options.hasLinearBuckets()) {
			buckets = linearBuckets(options);
		} else {
			throw new IllegalStateException("Unknown bucket type!");
		}
		buckets.add(new Bucket(Long.MAX_VALUE)); // overflow

		for (long value : snapshot.getValues()) {
			value = converter.convert(value);
			for (int i = 0; i < buckets.size(); i++) {
				if (buckets.get(i).add(value)) {
					break;
				}
			}
		}

		buckets.stream().map(b -> b.count).forEach(distribution::addBucketCounts);
	}

	private static List<Bucket> exponentialBuckets(BucketOptions options) {
		final Exponential exponential = options.getExponentialBuckets();
		final List<Bucket> buckets = new LinkedList<>();
		for (int i = 0; i <= exponential.getNumFiniteBuckets(); i++) {
			final long upper = round(exponential.getScale() * pow(exponential.getGrowthFactor(), i));
			buckets.add(new Bucket(upper));
		}
		return buckets;
	}

	private static List<Bucket> explicitBuckets(BucketOptions options) {
		var explicit = options.getExplicitBuckets();
		var buckets = new LinkedList<Bucket>();
		for (var upper : explicit.getBoundsList()) {
			buckets.add(new Bucket(upper.longValue()));
		}
		return buckets;
	}

	private static List<Bucket> linearBuckets(BucketOptions options) {
		var linear = options.getLinearBuckets();
		var buckets = new LinkedList<Bucket>();
		for (int i = 0; i <= linear.getNumFiniteBuckets(); i++) {
			var upper = round(linear.getOffset() + linear.getWidth() * i);
			buckets.add(new Bucket(upper));
		}
		return buckets;
	}

	/**
	 * Creates a new tag instance.
	 *
	 * @param name name of the tag.
	 * @param value the value if the tag.
	 * @return new tag instance.
	 */
	public static Tag tag(@Nonnull String name, @Nonnull String value) {
		return new Tag(name, value);
	}

	private static Optional<TimeUnit> timeUnit(@Nonnull String unit) {
		switch (unit.toLowerCase()) {
		case "ms":
			return Optional.of(MILLISECONDS);
		case "us":
			return Optional.of(MICROSECONDS);
		case "ns":
			return Optional.of(NANOSECONDS);
		default:
			return Optional.empty();
		}
	}


	// --- Inner Classes ---

	/**
	 *
	 */
	static class Context {
		final Config config;
		final com.google.api.MonitoredResource monitoredResource;
		final Timestamp startTime;
		final TimeInterval interval;

		Context(Config config, MonitoredResource monitoredResource, Timestamp startTime, TimeInterval interval) {
			this.config = config;
			this.monitoredResource = monitoredResource.toPb();
			this.startTime = startTime;
			this.interval = interval;
		}
	}

	/**
	 *
	 */
	interface Snapshot {
		/**
		 *
		 * @param context
		 * @param id
		 * @param descriptor
		 * @return
		 */
		default TimeSeries.Builder timeseries(
				Context ctx,
				MetricID id,
				MetricDescriptor descriptor)
		{
			var metricLabels = new HashMap<String, String>();
			id.getTags().forEach(metricLabels::put);
			return TIMESERIES_BUILDER.get()
					.setMetric(com.google.api.Metric.newBuilder().setType(descriptor.getType())
							.putAllLabels(metricLabels).build())
					.setResource(ctx.monitoredResource).setMetricKind(descriptor.getMetricKind())
					.setValueType(descriptor.getValueType());
		}
	}

	/**
	 *
	 */
	static class GaugeSnapshot implements Snapshot {
		private final Object value;

		GaugeSnapshot(Gauge<?> gauge) {
			this.value = gauge.getValue();
		}

		Object value() {
			return this.value;
		}

		private TypedValue value(MetricDescriptor descriptor) {
			var typedValue = TYPED_VALUE_BUILDER.get();
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
		public TimeSeries.Builder timeseries(
				Context ctx,
				MetricID id,
				MetricDescriptor descriptor)
		{
			var point = POINT_BUILDER.get()
					.setInterval(INTERVAL_BUILDER.get().setEndTime(ctx.interval.getEndTime()).build())
					.setValue(value(descriptor)).build();
			return Snapshot.super.timeseries(ctx, id, descriptor).addPoints(point);
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
		public TimeSeries.Builder timeseries(
				Context ctx,
				MetricID id,
				MetricDescriptor descriptor)
		{
			var point = POINT_BUILDER.get()
					.setInterval(INTERVAL_BUILDER.get().setEndTime(ctx.interval.getEndTime()).build())
					.setValue(TYPED_VALUE_BUILDER.get().setInt64Value(this.value).build()).build();
			return Snapshot.super.timeseries(ctx, id, descriptor).addPoints(point);
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
		public TimeSeries.Builder timeseries(
				Context ctx,
				MetricID id,
				MetricDescriptor descriptor)
		{
			var point = POINT_BUILDER.get()
					.setInterval(INTERVAL_BUILDER.get().mergeFrom(ctx.interval).setStartTime(ctx.startTime).build())
					.setValue(TYPED_VALUE_BUILDER.get().setInt64Value(this.value).build()).build();
			return Snapshot.super.timeseries(ctx, id, descriptor).addPoints(point);
		}
	}

	static class SamplingSnapshot implements Snapshot {
		private final org.eclipse.microprofile.metrics.Snapshot snapshot;

		SamplingSnapshot(Sampling sampling) {
			this.snapshot = sampling.getSnapshot();
		}

		@Override
		public Builder timeseries(
				Context ctx,
				MetricID id,
				MetricDescriptor descriptor)
		{
			var options = ctx.config.bucketOptions(TIMER, descriptor.getUnit());
			var distribution = DISTRIBUTION_BUILDER.get()
					.setMean(this.snapshot.getMean()).setCount(this.snapshot.size())
					.setSumOfSquaredDeviation(pow(this.snapshot.getStdDev(), 2)).setBucketOptions(options);

			// if a time unit then convert it
			var timeUnit = timeUnit(descriptor.getUnit());
			buckets(options, snapshot, l -> timeUnit.map(tu -> tu.convert(l, NANOSECONDS)).orElse(l), distribution);

			var builder = Snapshot.super.timeseries(ctx, id, descriptor);
			var point = POINT_BUILDER.get()
					.setInterval(INTERVAL_BUILDER.get().mergeFrom(ctx.interval).clearStartTime().build())
					.setValue(TYPED_VALUE_BUILDER.get().setDistributionValue(distribution).build()).build();
			return builder.addPoints(point);
		}
	}

	static class MeteredSnapshot implements Snapshot {
		private final long count;

		MeteredSnapshot(Metered metered) {
			this.count = metered.getCount();
		}

		@Override
		public TimeSeries.Builder timeseries(
				Context ctx,
				MetricID id,
				MetricDescriptor descriptor)
		{
			var point = POINT_BUILDER.get()
					.setInterval(INTERVAL_BUILDER.get().mergeFrom(ctx.interval).clearStartTime().build())
					.setValue(TYPED_VALUE_BUILDER.get().setInt64Value(this.count).build()).build();
			return Snapshot.super.timeseries(ctx, id, descriptor).addPoints(point);
		}
	}

	private static class Bucket {
		private final long upper;
		private int count;

		Bucket(long upper) {
			this.upper = upper;
		}

		/**
		 *
		 * @param value the value to add.
		 * @return {@code true} if the value was added (i.e. in this bucket).
		 */
		boolean add(long value) {
			// these are processed in order so lower not needed
			if (value < upper) {
				count++;
				return true;
			}
			return false;
		}
	}

	@FunctionalInterface
	interface BucketConverter {
		long convert(long l);
	}
}
