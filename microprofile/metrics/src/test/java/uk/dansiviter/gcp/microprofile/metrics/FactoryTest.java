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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.Distribution;
import com.google.api.Distribution.BucketOptions;
import com.google.api.Distribution.BucketOptions.Explicit;
import com.google.api.Distribution.BucketOptions.Exponential;
import com.google.api.Distribution.BucketOptions.Linear;
import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.ValueType;
import com.google.cloud.MonitoredResource;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue.ValueCase;
import com.google.protobuf.Timestamp;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.gcp.microprofile.metrics.Factory.Context;
import uk.dansiviter.gcp.microprofile.metrics.Factory.GaugeSnapshot;
import uk.dansiviter.gcp.microprofile.metrics.Factory.Snapshot;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [18 Dec 2019]
 */
@ExtendWith(MockitoExtension.class)
public class FactoryTest {
	private static final long[] VALUES = new long[] { 0, 1, 3, 9, 15, 5_000, 50_000 };

	@Test
	public void toDescriptor(@Mock Config config, @Mock MetricRegistry registry, @Mock MetricID id, @Mock GaugeSnapshot snapshot, @Mock Metadata metadata) {
		when(id.getName()).thenReturn("id");
		when(registry.getMetadata()).thenReturn(Map.of("id", metadata));
		when(snapshot.value()).thenReturn(123L);
		when(metadata.getDisplayName()).thenReturn("displayName");

		MetricDescriptor actual = Factory.toDescriptor(config, registry, Type.APPLICATION, id, snapshot);
		assertEquals(actual.getDisplayName(), "displayName");
		assertEquals(ValueType.INT64, actual.getValueType());
	}

	@Test
	public void toDescriptor_unknownType(@Mock Config config, @Mock MetricRegistry registry, @Mock MetricID id, @Mock Snapshot snapshot, @Mock Metadata metadata) {
		when(id.getName()).thenReturn("id");
		when(registry.getMetadata()).thenReturn(Map.of("id", metadata));
		when(metadata.getDisplayName()).thenReturn("displayName");

		MetricDescriptor actual = Factory.toDescriptor(config, registry, Type.APPLICATION, id, snapshot);
		assertEquals(actual.getDisplayName(), "displayName");
		assertEquals(ValueType.VALUE_TYPE_UNSPECIFIED, actual.getValueType());
	}

	@Test
	public void toInterval() {
		final ZonedDateTime start = ZonedDateTime.of(1970, 1, 2, 3, 4, 5, 6, ZoneOffset.UTC);
		final ZonedDateTime end = ZonedDateTime.of(1970, 3, 4, 5, 6, 7, 8, ZoneOffset.UTC);

		final TimeInterval actual = Factory.toInterval(start, end);
		assertEquals(97445L, actual.getStartTime().getSeconds());
		assertEquals(6, actual.getStartTime().getNanos());
		assertEquals(5375167L, actual.getEndTime().getSeconds());
		assertEquals(8, actual.getEndTime().getNanos());
	}

	@Test
	public void toSnapshot(@Mock Metric metric) {
		Optional<Snapshot> snapshot = Factory.toSnapshot(metric);

		assertTrue(snapshot.isEmpty());
	}

	@Test
	public void toSnapshot_gauge(@Mock Gauge<?> metric) {
		Optional<Snapshot> snapshot = Factory.toSnapshot(metric);

		assertFalse(snapshot.isEmpty());
		verify(metric).getValue();
	}

	@Test
	public void toSnapshot(@Mock ConcurrentGauge metric) {
		Optional<Snapshot> snapshot = Factory.toSnapshot(metric);

		assertFalse(snapshot.isEmpty());
		verify(metric).getCount();
	}

	@Test
	public void guageSnapshot_timeseries(
			@Mock Gauge<Long> gauge,
			@Mock Config config,
			@Mock MetricID id)
	{
		MetricDescriptor descriptor = MetricDescriptor.newBuilder().setValueType(ValueType.INT64).build();
		MonitoredResource monitoredResource = MonitoredResource.newBuilder("global").build();
		TimeInterval interval = TimeInterval.newBuilder().build();

		when(gauge.getValue()).thenReturn(123L);
		GaugeSnapshot gaugeSnapshot = new GaugeSnapshot(gauge);
		Context ctx = new Context(config, monitoredResource, Timestamp.getDefaultInstance(), interval);
		TimeSeries.Builder builder = gaugeSnapshot.timeseries(ctx, id, descriptor);
		TimeSeries timeSeries = builder.build();
		assertEquals(1, timeSeries.getPointsCount());

		Point point = timeSeries.getPoints(0);
		assertEquals(Timestamp.getDefaultInstance(), point.getInterval().getStartTime());
		assertEquals(interval.getEndTime(), point.getInterval().getEndTime());
		assertEquals(ValueCase.INT64_VALUE, point.getValue().getValueCase());
		assertEquals(123L, point.getValue().getInt64Value());
	}

	@Test
	public void buckets_exponential(
			@Mock org.eclipse.microprofile.metrics.Snapshot snapshot)
	{
		Distribution.Builder builder = Distribution.newBuilder();
		Exponential exponential = Exponential.newBuilder()
				.setNumFiniteBuckets(5)
				.setScale(1)
				.setGrowthFactor(2)
				.build();
		BucketOptions options = BucketOptions.newBuilder().setExponentialBuckets(exponential).build();
		when(snapshot.getValues()).thenReturn(new long[] { 0, 1, 3, 9, 15, 5_000, 50_000 });

		Factory.buckets(options, snapshot, l -> l, builder);

		Distribution distribution = builder.build();
		assertEquals(7, distribution.getBucketCountsCount());
		assertEquals(List.of(1L, 1L, 1L, 0L, 2L, 0L, 2L), distribution.getBucketCountsList());
	}

	@Test
	public void buckets_linear(
			@Mock org.eclipse.microprofile.metrics.Snapshot snapshot)
	{
		Distribution.Builder builder = Distribution.newBuilder();
		Linear linear = Linear.newBuilder()
				.setNumFiniteBuckets(5)
				.setOffset(3)
				.setWidth(4)
				.build();
		BucketOptions options = BucketOptions.newBuilder().setLinearBuckets(linear).build();
		when(snapshot.getValues()).thenReturn(new long[] { 0, 1, 3, 9, 15, 5_000, 50_000 });

		Factory.buckets(options, snapshot, l -> l, builder);

		Distribution distribution = builder.build();
		assertEquals(7, distribution.getBucketCountsCount());
		assertEquals(List.of(2L, 1L, 1L, 0L, 1L, 0L, 2L), distribution.getBucketCountsList());
	}

	@Test
	public void buckets_explicit(
			@Mock org.eclipse.microprofile.metrics.Snapshot snapshot)
	{
		Distribution.Builder builder = Distribution.newBuilder();
		Explicit explicit = Explicit.newBuilder()
				.addBounds(2)
				.addBounds(5)
				.addBounds(20)
				.addBounds(1000)
				.build();
		BucketOptions options = BucketOptions.newBuilder().setExplicitBuckets(explicit).build();
		when(snapshot.getValues()).thenReturn(VALUES);

		Factory.buckets(options, snapshot, l -> l, builder);

		Distribution distribution = builder.build();
		assertEquals(5, distribution.getBucketCountsCount());
		assertEquals(List.of(2L, 1L, 2L, 0L, 2L), distribution.getBucketCountsList());
	}
}