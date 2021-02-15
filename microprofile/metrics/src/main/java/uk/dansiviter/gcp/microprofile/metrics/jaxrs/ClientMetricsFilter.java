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
import static uk.dansiviter.gcp.microprofile.metrics.Factory.registryType;
import static uk.dansiviter.gcp.microprofile.metrics.Factory.tag;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.REQUEST_COUNT;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.RESPONSE_COUNT;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.RESPONSE_LATENCY;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.path;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.status;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.targetHost;

import java.io.IOException;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.container.PreMatching;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * @author Daniel Siviter
 * @since v1.0 [11 Feb 2020]
 */
@PreMatching
@ApplicationScoped
@Priority(1)
class ClientMetricsFilter implements ClientRequestFilter, ClientResponseFilter {
	static final String START = ClientMetricsFilter.class.getName() + "-start";
	static final Tag KIND = tag("kind", "client");

	@Override
	public void filter(ClientRequestContext req) throws IOException {
		final MetricRegistry registry = metricRegistry();
		registry.counter(REQUEST_COUNT, KIND, targetHost(req.getUri()), path(req.getUri())).inc();
		req.setProperty(START, now());
	}

	@Override
	public void filter(ClientRequestContext req, ClientResponseContext res) throws IOException {
		final MetricRegistry registry = metricRegistry();
		final Tag[] tags = { KIND, targetHost(req.getUri()), path(req.getUri()), status(res.getStatus()) };
		final Timer timer = registry.timer(RESPONSE_LATENCY, tags);
		req.setProperty(RESPONSE_LATENCY.getName(), timer);
		registry.counter(RESPONSE_COUNT, tags).inc();
	}

	private MetricRegistry metricRegistry() {
		return CDI.current().select(MetricRegistry.class, registryType(APPLICATION)).get();
	}
}
