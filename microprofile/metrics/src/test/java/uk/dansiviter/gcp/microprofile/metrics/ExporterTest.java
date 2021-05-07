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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.dansiviter.gcp.microprofile.metrics.ReflectionUtil.set;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.api.MetricDescriptor;
import com.google.cloud.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;

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
	private ScheduledExecutorService executor;

	private Exporter exporter;

	@BeforeEach
	void before() {
		this.exporter = new Exporter(() -> resource);
		set(this.exporter, "executor", executor);
		set(this.exporter, "samplingRate", SamplingRate.STANDARD);
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
		set(this.exporter, "client", client);

		this.exporter.init(null);

		verify(this.executor).scheduleAtFixedRate(any(), eq(10L), eq(60L), eq(TimeUnit.SECONDS));
	}

	@Test
	void destroy(@Mock ScheduledFuture<?> future) {
		set(this.exporter, "future", future);

		this.exporter.destroy();

		verify(future).cancel(false);
	}
}
