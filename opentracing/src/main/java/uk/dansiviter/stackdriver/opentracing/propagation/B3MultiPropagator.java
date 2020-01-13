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
package uk.dansiviter.stackdriver.opentracing.propagation;

import java.util.Map;

import io.opentracing.propagation.TextMap;
import uk.dansiviter.stackdriver.opentracing.StackdriverSpanContext;

/**
 * @author Daniel Siviter
 * @since v1.0 [15 Dec 2019]
 */
public class B3MultiPropagator implements TextMapPropagator {
	protected static final String TRACE_ID = "X-B3-TraceId";
	protected static final String SPAN_ID = "X-B3-SpanId";
	protected static final String PARENT_SPAN_ID = "X-B3-ParentSpanId";
	protected static final String SAMPLED = "X-B3-Sampled";
	protected static final String FLAGS = "X-B3-Flags";

	@Override
	public void inject(StackdriverSpanContext spanContext, TextMap carrier) {
		carrier.put(TRACE_ID, spanContext.traceId());
		carrier.put(SPAN_ID, Long.toHexString(spanContext.spanId()).toLowerCase());
		spanContext.parentSpanId().ifPresent(v -> carrier.put(PARENT_SPAN_ID, Long.toHexString(v).toLowerCase()));
		carrier.put(SAMPLED, spanContext.sampled() ? "1" : "0");
		// carrier.put(FLAGS, spanContext.flags());
	}

	@Override
	public StackdriverSpanContext extract(TextMap carrier) {
		String traceId = null;
		String spanId = null;
		String parentSpanId = null;
		String sampled = null;

		for (Map.Entry<String, String> e : carrier) {
			switch (e.getKey()) {
				case TRACE_ID:
					traceId = e.getValue();
					break;
				case SPAN_ID:
					spanId = e.getValue();
					break;
				case PARENT_SPAN_ID:
					parentSpanId = e.getValue();
					break;
				case SAMPLED:
					sampled = e.getValue();
					break;
				default:
					break;
			}
		}

		if (spanId == null || traceId == null) {
			throw new IllegalArgumentException("spanId or traceId missing!");
		}

		final StackdriverSpanContext.Builder builder = StackdriverSpanContext.builder(traceId, spanId).sampled("1".equals(sampled));
		if (parentSpanId != null) {
			builder.parentSpanId(parentSpanId);
		}
		return builder.build();
	}
}
