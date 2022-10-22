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

import static java.time.Duration.between;
import static java.time.Instant.now;
import static jakarta.ws.rs.Priorities.USER;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.ContainerMetricsFilter.START;
import static uk.dansiviter.gcp.microprofile.metrics.jaxrs.Metrics.RESPONSE_LATENCY;

import java.io.IOException;
import java.time.Instant;

import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.eclipse.microprofile.metrics.Timer;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [3 Feb 2020]
 */
@Priority(USER + 1_000)
class ContainerMetricsWriterInterceptor implements WriterInterceptor {
	@Override
	public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
		try {
			context.proceed();
		} finally {
			var timer = (Timer) context.getProperty(RESPONSE_LATENCY.getName());
			if (timer != null) {
				var stop = now();
				var start = (Instant) context.getProperty(START);
				timer.update(between(start, stop));
			}
		}
	}
}
