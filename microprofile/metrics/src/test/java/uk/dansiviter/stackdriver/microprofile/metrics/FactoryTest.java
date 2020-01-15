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
package uk.dansiviter.stackdriver.microprofile.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.ValueType;
import com.google.monitoring.v3.TimeInterval;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.stackdriver.microprofile.metrics.Factory.GaugeSnapshot;
import uk.dansiviter.stackdriver.microprofile.metrics.Factory.Snapshot;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [18 Dec 2019]
 */
@ExtendWith({ WeldJunit5Extension.class, MockitoExtension.class })
public class FactoryTest {
	@WeldSetup
    public WeldInitiator weld = WeldInitiator.of(Factory.class, FactoryTest.class);

	@Inject
	private Factory factory;

	@Produces
    @ConfigProperty
    public String configProperty(InjectionPoint ip) {
		final ConfigProperty cp = ip.getAnnotated().getAnnotation(ConfigProperty.class);
		assertEquals("stackdriver.prefix", cp.name());
		return "foo";
	}

	@Test
	public void toDescriptor(@Mock MetricRegistry registry, @Mock MetricID id, @Mock GaugeSnapshot snapshot, @Mock Metadata metadata) {
		when(id.getName()).thenReturn("id");
		when(registry.getMetadata()).thenReturn(Map.of("id", metadata));
		when(snapshot.value()).thenReturn(123L);
		when(metadata.getDisplayName()).thenReturn("displayName");

		MetricDescriptor actual = this.factory.toDescriptor(registry, id, snapshot);
		assertEquals(actual.getDisplayName(), "displayName");
		assertEquals(ValueType.INT64, actual.getValueType());
	}

	@Test
	public void toDescriptor_unknownType(@Mock MetricRegistry registry, @Mock MetricID id, @Mock Snapshot snapshot, @Mock Metadata metadata) {
		when(id.getName()).thenReturn("id");
		when(registry.getMetadata()).thenReturn(Map.of("id", metadata));
		when(metadata.getDisplayName()).thenReturn("displayName");

		MetricDescriptor actual = this.factory.toDescriptor(registry, id, snapshot);
		assertEquals(actual.getDisplayName(), "displayName");
		assertEquals(ValueType.VALUE_TYPE_UNSPECIFIED, actual.getValueType());
	}

	@Test
	public void toInterval() {
		final ZonedDateTime start = ZonedDateTime.of(1970, 1, 2, 3, 4, 5, 6, ZoneOffset.UTC);
		final ZonedDateTime end = ZonedDateTime.of(1970, 3, 4, 5, 6, 7, 8, ZoneOffset.UTC);

		final TimeInterval actual = this.factory.toInterval(start, end);
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
}
