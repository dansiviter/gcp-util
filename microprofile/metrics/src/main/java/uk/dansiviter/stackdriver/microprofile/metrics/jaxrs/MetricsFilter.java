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
package uk.dansiviter.stackdriver.microprofile.metrics.jaxrs;

import static java.time.Instant.now;
import static org.eclipse.microprofile.metrics.MetricType.COUNTER;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;
import static uk.dansiviter.stackdriver.microprofile.metrics.Factory.tag;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Simple JAX-RS filter performing stats collection of requests/responses.
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Jan 2020]
 */
@Provider
@PreMatching
@ApplicationScoped
@Priority(1)
public class MetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {
	static final Metadata REQUEST_COUNT = Metadata.builder()
			.withName("request.count")
			.withDisplayName("Request Count")
			.withDescription("The number of requests receieved by JAX-RS.")
			.withType(COUNTER)
			.build();
	static final Metadata RESPONSE_COUNT = Metadata.builder()
			.withName("response.count")
			.withDisplayName("Response Count")
			.withDescription("The number of requests receieved by JAX-RS.")
			.withType(COUNTER)
			.build();
	static final Metadata RESPONSE_LATENCY = Metadata.builder()
			.withName("request.latency")
			.withDisplayName("Request Latency")
			.withDescription("The time it took for the application code to process the request and respond.")
			.withUnit(MILLISECONDS)
			.withType(TIMER)
			.build();
	static final String START = MetricsFilter.class.getName() + "-start";

	@Inject
	private MetricRegistry registry;

	@Override
	public void filter(ContainerRequestContext req) {
		this.registry.counter(REQUEST_COUNT, path(req.getUriInfo())).inc();
		req.setProperty(START, now());
	}

	@Override
	public void filter(ContainerRequestContext req, ContainerResponseContext res) {
		final Tag[] tags = { path(req.getUriInfo()), status(res) };
		final Timer timer = this.registry.timer(RESPONSE_LATENCY, tags);
		req.setProperty(RESPONSE_LATENCY.getName(), timer);
		this.registry.counter(RESPONSE_COUNT, path(req.getUriInfo()), status(res)).inc();
	}

	private static Tag path(UriInfo uri) {
		return tag("path", uri.getPath());
	}

	private static Tag status(ContainerResponseContext res) {
		return tag("response_code", Integer.toString(res.getStatus()));
	}
}
