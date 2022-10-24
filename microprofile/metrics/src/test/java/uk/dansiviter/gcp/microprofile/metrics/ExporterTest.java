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

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.dansiviter.gcp.microprofile.metrics.ReflectionUtil.set;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import com.google.api.MetricDescriptor;
import com.google.cloud.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.stub.MetricServiceStub;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.gcp.microprofile.metrics.Exporter.SamplingRate;

/**
 * Test for {@link Exporter}.
 */
@ExtendWith(MockitoExtension.class)
class ExporterTest {
	private final MonitoredResource resource = MonitoredResource.of("global", Map.of("project_id", "my_project"));

	@Mock
	Logger log;
	@Mock
	ScheduledExecutorService executor;
	@Mock
	MetricRegistry baseRegistry;
	@Mock
	MetricRegistry vendorRegistry;
	@Mock
	MetricRegistry appRegistry;

	private Exporter exporter;

	@BeforeEach
	void before() {
		this.exporter = new Exporter(() -> resource);
		set(this.exporter, "executor", executor);
		set(this.exporter, "enabled", true);
		set(this.exporter, "samplingRate", SamplingRate.STANDARD);
		set(this.exporter, "log", this.log);
		set(this.exporter, "baseRegistry", this.baseRegistry);
		set(this.exporter, "vendorRegistry", this.vendorRegistry);
		set(this.exporter, "appRegistry", this.appRegistry);
	}

	@Test
	void like() {
		var d0 = MetricDescriptor.newBuilder().build();
		var d1 = MetricDescriptor.newBuilder().build();
		assertThat("Are not alike", Exporter.like(d0, d1));

		d0 = MetricDescriptor.newBuilder().addMonitoredResourceTypes("global").build();
		d1 = MetricDescriptor.newBuilder().build();
		assertThat("Are not alike", Exporter.like(d0, d1));

		d0 = MetricDescriptor.newBuilder().addMonitoredResourceTypes("global").build();
		d1 = MetricDescriptor.newBuilder().addMonitoredResourceTypes("global").build();
		assertThat("Are not alike", Exporter.like(d0, d1));

		d0 = MetricDescriptor.newBuilder().setName("foo").build();
		d1 = MetricDescriptor.newBuilder().build();
		assertThat("Are alike", !Exporter.like(d0, d1));
	}

	@Test
	void init(@Mock MetricServiceClient client) {
		set(this.exporter, "client", new TestInstance<>(client));

		this.exporter.init(null);

		verify(this.executor).scheduleAtFixedRate(any(), eq(10L), eq(60L), eq(TimeUnit.SECONDS));
	}

	@Test
	void flush(@Mock MetricServiceStub clientStub) {
		set(this.exporter, "client", new TestInstance<>(MetricServiceClient.create(clientStub)));
		set(this.exporter, "startInstant", Instant.now().minus(1L, SECONDS));
		set(this.exporter, "resource", resource);

		this.exporter.flush();

		verify(this.log, never()).collectionFail(any());
	}

	@Test
	void destroy(@Mock MetricServiceStub clientStub, @Mock ScheduledFuture<?> future) {
		set(this.exporter, "client", new TestInstance<>(MetricServiceClient.create(clientStub)));
		set(this.exporter, "future", future);
		var startInstant = Instant.now().minus(1L, SECONDS);
		set(this.exporter, "startInstant",startInstant);
		set(this.exporter, "previousInstant", startInstant.plus(500, MILLIS));
		set(this.exporter, "resource", resource);

		this.exporter.destroy();

		verify(future).cancel(false);
		verify(clientStub).close();
	}


	private static class TestInstance<T> implements Instance<T> {
		private final T value;

		TestInstance(T value) {
			this.value = value;
		}

		@Override
		public Iterator<T> iterator() {
			return Collections.singleton(value).iterator();
		}

		@Override
		public T get() {
			return value;
		}

		@Override
		public Instance<T> select(Annotation... qualifiers) {
			return this;
		}

		@Override
		public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isUnsatisfied() {
			return false;
		}

		@Override
		public boolean isAmbiguous() {
			return false;
		}

		@Override
		public void destroy(T instance) {
			// nothing to see here
		}
	}
}
