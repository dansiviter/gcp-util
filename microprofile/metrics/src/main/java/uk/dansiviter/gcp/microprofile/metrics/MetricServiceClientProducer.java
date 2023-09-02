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

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import com.google.cloud.monitoring.v3.MetricServiceClient;

/**
 * Produces an instance of {@link MetricServiceClientProducer}.
 * <p>
 * Override with the {@link jakarta.enterprise.inject.Specializes} mechanism if you need to override.
 * <p>
 * <strong>Note:</strong> The {@code client} will be shutdown by the {@link Exporter} to ensure it persists the final
 * data.
 */
@ApplicationScoped
public class MetricServiceClientProducer {
	@Inject
	private Log log;

	@Produces
	private MetricServiceClient client;

	@PostConstruct
	public void init() {
		this.client = createClient();
	}

	protected MetricServiceClient createClient() {
		try {
			return MetricServiceClient.create();
		} catch (IOException e) {
			this.log.clientInitError(e.getMessage());
			return null;
		}
	}
}
