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
package uk.dansiviter.gcp.microprofile.metrics.jaxrs;


import static uk.dansiviter.gcp.microprofile.metrics.Factory.tag;

import java.net.URI;

import static org.eclipse.microprofile.metrics.MetricType.COUNTER;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * @author Daniel Siviter
 * @since v1.0 [11 Feb 2020]
 */
public enum Metrics { ;
	static final Metadata REQUEST_COUNT = Metadata.builder()
			.withName("jaxrs/request.count")
			.withDisplayName("Request Count")
			.withDescription("The number of requests receieved by JAX-RS.")
			.withType(COUNTER)
			.build();
	static final Metadata RESPONSE_COUNT = Metadata.builder()
			.withName("jaxrs/response.count")
			.withDisplayName("Response Count")
			.withDescription("The number of requests receieved by JAX-RS.")
			.withType(COUNTER)
			.build();
	static final Metadata RESPONSE_LATENCY = Metadata.builder()
			.withName("jaxrs/request.latency")
			.withDisplayName("Request Latency")
			.withDescription("The time it took for the application code to process the request and respond.")
			.withUnit(MILLISECONDS)
			.withType(TIMER)
			.build();

	static Tag path(URI uri) {
		return tag("path", uri.getPath());
	}

	static Tag status(int status) {
		return tag("response_code", Integer.toString(status));
	}

	static Tag targetHost(URI uri) {
		return tag("target_host", uri.getHost());
	}
}
