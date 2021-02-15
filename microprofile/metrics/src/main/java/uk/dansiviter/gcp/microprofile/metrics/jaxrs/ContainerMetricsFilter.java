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

import static java.time.Instant.now;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static uk.dansiviter.gcp.microprofile.metrics.Factory.tag;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.REQUEST_COUNT;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.RESPONSE_COUNT;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.RESPONSE_LATENCY;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.path;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.status;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * Simple JAX-RS filter performing stats collection of requests/responses.
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Jan 2020]
 */
@PreMatching
@ApplicationScoped
@Priority(1)
class ContainerMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {
	static final String START = ContainerMetricsFilter.class.getName() + "-start";
	static final Tag KIND = tag("kind", "server");

	@Inject
	@RegistryType(type = APPLICATION)
	private MetricRegistry registry;

	@Override
	public void filter(ContainerRequestContext req) {
		this.registry.counter(REQUEST_COUNT, KIND, path(req.getUriInfo().getRequestUri())).inc();
		req.setProperty(START, now());
	}

	@Override
	public void filter(ContainerRequestContext req, ContainerResponseContext res) {
		final Tag[] tags = { KIND, path(req.getUriInfo().getRequestUri()), status(res.getStatus()) };
		final Timer timer = this.registry.timer(RESPONSE_LATENCY, tags);
		req.setProperty(RESPONSE_LATENCY.getName(), timer);
		this.registry.counter(RESPONSE_COUNT, tags).inc();
	}
}
