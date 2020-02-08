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

import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static javax.ws.rs.Priorities.USER;
import static uk.dansiviter.stackdriver.microprofile.metrics.jaxrs.MetricsFilter.RESPONSE_LATENCY;
import static uk.dansiviter.stackdriver.microprofile.metrics.jaxrs.MetricsFilter.START;

import java.io.IOException;
import java.time.Instant;

import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import org.eclipse.microprofile.metrics.Timer;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [3 Feb 2020]
 * @see Filter
 */
@Priority(USER + 1_000)
class MetricsInterceptor implements WriterInterceptor {
	@Override
	public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
		try {
			context.proceed();
		} finally {
			final Instant stop = now();
			final Instant start = (Instant) context.getProperty(START);
			final Timer timer = (Timer) context.getProperty(RESPONSE_LATENCY.getName());
			timer.update(between(start, stop).toNanos(), NANOSECONDS);
		}
	}
}
