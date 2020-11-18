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
package uk.dansiviter.gcp.monitoring.opentelemetry;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;

/**
 *
 * @since v1.0 [20 Feb 2020]
 */
public class CloudTracerProvider implements TracerProvider {
	@Override
	public Tracer get(String instrumentationName) {
		if ("stackdriver".equalsIgnoreCase(instrumentationName)) {
			return CloudTracer.builder().build();
		}
		return Tracer.getDefault();
	}

	@Override
	public Tracer get(String instrumentationName, String instrumentationVersion) {
		return get(instrumentationName);
	}
}
