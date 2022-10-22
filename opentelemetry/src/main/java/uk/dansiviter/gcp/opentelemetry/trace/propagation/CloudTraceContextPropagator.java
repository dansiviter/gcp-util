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
package uk.dansiviter.gcp.opentelemetry.trace.propagation;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * TRACE_ID/SPAN_ID;o=TRACE_TRUE
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Feb 2021]
 */
public class CloudTraceContextPropagator implements TextMapPropagator {
	private static final CloudTraceContextPropagator INSTANCE = new CloudTraceContextPropagator();
	static final String HEADER = "X-Cloud-Trace-Context";
	private static final List<String> FIELDS = List.of(HEADER);

	@Override
	public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
		SpanContext spanContext = Span.fromContext(context).getSpanContext();
		if (!spanContext.isValid()) {
			return;
		}
		var buf = new StringBuilder(spanContext.getTraceId())
			.append('/')
			.append(spanContext.getSpanId())
			.append(";o=")
			.append(spanContext.isSampled() ? "1" : "0");
		setter.set(carrier, HEADER, buf.toString());
	}

	@Override
	public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
		String value = getter.get(carrier, HEADER);

		if (value == null) {
			return context;
		}

		final String[] tokens = value.split("\\/|(?:\\;o=)");
		if (tokens.length == 1 && "0".equals(tokens[0])) {
			return context;
		}

		SpanContext spanContext = SpanContext.createFromRemoteParent(
			tokens[0],
			tokens[1],
			"1".equals(tokens[2]) ? TraceFlags.getSampled() : TraceFlags.getDefault(),
			TraceState.getDefault());
		return context.with(Span.wrap(spanContext));
	}

	@Override
	public Collection<String> fields() {
		return FIELDS;
	}

	// --- Static Methods ---

	/**
	 * @return cloud trace propagator instance.
	 */
	public static CloudTraceContextPropagator cloudTraceContextPropagator() {
		return INSTANCE;
	}
}
