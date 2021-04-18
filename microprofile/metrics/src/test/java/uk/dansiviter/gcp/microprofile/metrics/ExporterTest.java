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

import com.google.api.MetricDescriptor;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Exporter}.
 */
class ExporterTest {
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
}
