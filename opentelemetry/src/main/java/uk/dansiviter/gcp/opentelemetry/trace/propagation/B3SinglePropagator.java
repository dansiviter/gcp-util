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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * @author Daniel Siviter
 * @since v1.0 [13 Feb 2021]
 */
public class B3SinglePropagator implements TextMapPropagator {
	private static final B3SinglePropagator INSTANCE = new B3SinglePropagator();
	static final String B3 = "B3";
	private static final List<String> FIELDS = List.of(B3);

	@Override
	public <C> void inject(Context context, C carrier, Setter<C> setter) {
		SpanContext spanContext = Span.fromContext(context).getSpanContext();
		if (!spanContext.isValid()) {
			return;
		}
		var buf = new StringBuilder(spanContext.getTraceId())
			.append('-')
			.append(spanContext.getSpanId())
			.append('-')
			.append(spanContext.isSampled() ? "1" : "0");
		setter.set(carrier, B3, buf.toString());
	}

	@Override
	public <C> Context extract(Context context, C carrier, Getter<C> getter) {
		String value = getter.get(carrier, B3);

		if (value == null) {
			return context;
		}

		final String[] tokens = value.split("-");
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

	public static B3SinglePropagator b3SinglePropagator() {
		return INSTANCE;
	}
}
