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
package uk.dansiviter.gcp.monitoring;

import static java.util.concurrent.Executors.newCachedThreadPool;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener;
import org.glassfish.jersey.jsonb.JsonBindingFeature;

import io.helidon.common.context.Contexts;
import io.opentracing.contrib.concurrent.TracedExecutorService;
import io.opentracing.util.GlobalTracer;
import uk.dansiviter.gcp.monitoring.metrics.jaxrs.ClientMetricsFeature;

/**
 * @author Daniel Siviter
 * @since v1.0 [11 Feb 2020]
 */
public class RestClientBuilderHandler implements RestClientBuilderListener {
	@Override
	public void onNewBuilder(RestClientBuilder builder) {
		builder.register(JsonBindingFeature.class);
		builder.register(ClientMetricsFeature.class);
		builder.executorService(Contexts.wrap(new TracedExecutorService(newCachedThreadPool(), GlobalTracer.get())));
	}
}
