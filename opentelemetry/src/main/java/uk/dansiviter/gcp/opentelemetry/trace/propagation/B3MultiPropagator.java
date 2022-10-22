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
 * @author Daniel Siviter
 * @since v1.0 [13 Feb 2021]
 */
public class B3MultiPropagator implements TextMapPropagator {
	private static final B3MultiPropagator INSTANCE = new B3MultiPropagator();
	static final String TRACE_ID = "x-b3-traceid";
	static final String SPAN_ID = "x-b3-spanid";
	static final String PARENT_SPAN_ID = "x-b3-parentSpanid";
	static final String SAMPLED = "x-b3-sampled";
	static final String FLAGS = "x-b3-flags";
	private static final List<String> FIELDS = List.of(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SAMPLED, FLAGS);

	@Override
	public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
		SpanContext spanContext = Span.fromContext(context).getSpanContext();
		if (!spanContext.isValid()) {
			return;
		}

		setter.set(carrier, TRACE_ID, spanContext.getTraceId());
		setter.set(carrier, SPAN_ID, spanContext.getSpanId());
		String parentSpanId = spanContext.getTraceState().get(PARENT_SPAN_ID);
		if (parentSpanId != null) {
			setter.set(carrier, PARENT_SPAN_ID, parentSpanId);
		}
		setter.set(carrier, SAMPLED, spanContext.isSampled() ? "1" : "0");
	}


	@Override
	public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
		String traceId = getter.get(carrier, TRACE_ID);
		String spanId = getter.get(carrier, SPAN_ID);
		String parentSpanId = getter.get(carrier, PARENT_SPAN_ID);
		String sampled =  getter.get(carrier, SAMPLED);

		if (spanId == null || traceId == null) {
			return context;
		}

		TraceState state;
		if (parentSpanId != null) {
			state = TraceState.builder().put(PARENT_SPAN_ID, parentSpanId).build();
		} else {
			state = TraceState.getDefault();
		}

		SpanContext spanContext = SpanContext.createFromRemoteParent(
				traceId,
				spanId,
				"1".equals(sampled) ? TraceFlags.getSampled() : TraceFlags.getDefault(),
				state);
		return context.with(Span.wrap(spanContext));
	}

	@Override
	public Collection<String> fields() {
		return FIELDS;
	}


	// --- Static Methods ---

	/**
	 * @return B3 multi-header propagator.
	 */
	public static B3MultiPropagator b3MultiPropagator() {
		return INSTANCE;
	}
}
