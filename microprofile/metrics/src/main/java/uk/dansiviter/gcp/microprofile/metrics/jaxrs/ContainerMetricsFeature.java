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

import static jakarta.ws.rs.RuntimeType.SERVER;

import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;

/**
 * @author Daniel Siviter
 * @since v1.0 [8 Feb 2020]
 */
@Provider
@ConstrainedTo(SERVER)
public class ContainerMetricsFeature implements Feature {
	@Override
	public boolean configure(FeatureContext context) {
		context.register(ContainerMetricsFilter.class);
		context.register(ContainerMetricsWriterInterceptor.class);
		return true;
	}
}
