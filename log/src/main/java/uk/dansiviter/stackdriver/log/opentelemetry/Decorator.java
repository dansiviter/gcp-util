/*
 * Copyright 2019 Daniel Siviter
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
package uk.dansiviter.stackdriver.log.opentelemetry;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.google.cloud.ServiceOptions;
import com.google.cloud.logging.LogEntry.Builder;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import uk.dansiviter.stackdriver.log.Entry;
import uk.dansiviter.stackdriver.log.EntryDecorator;

/**
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public class Decorator implements EntryDecorator {
	private final String prefix;
	private final Tracer tracer;

	/**
	 *
	 */
	public Decorator() {
		// FIXME I really don't understand the OpenTelemetry API, but this'll do for the time being
		this(OpenTelemetry.getTracerFactory().get("foo"));
	}

	/**
	 * @param tracer
	 */
	public Decorator(@Nonnull Tracer tracer) {
		this(tracer, Optional.of(ServiceOptions.getDefaultProjectId()));
	}

	/**
	 *
	 * @param tracer
	 * @param projectId
	 */
	public Decorator(@Nonnull Tracer tracer, Optional<String> projectId) {
		this.prefix = String.format("projects/%s/traces/", projectId.orElse(""));
		this.tracer = tracer;
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		final SpanContext ctx = this.tracer.getCurrentSpan().getContext();
		b.setTrace(this.prefix + ctx.getTraceId().toLowerBase16());
		b.setSpanId(ctx.getSpanId().toLowerBase16());
		b.setTraceSampled(ctx.getTraceFlags().isSampled());
	}
}
