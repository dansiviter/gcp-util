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
package uk.dansiviter.gcp.monitoring.opentracing.propagation;

import java.util.Map;

import io.opentracing.propagation.TextMap;
import uk.dansiviter.gcp.monitoring.opentracing.CloudTraceSpanContext;

/**
 * @author Daniel Siviter
 * @since v1.0 [15 Dec 2019]
 */
public class B3MultiPropagator implements TextMapPropagator {
	protected static final String TRACE_ID = "x-b3-traceid";
	protected static final String SPAN_ID = "x-b3-spanid";
	protected static final String PARENT_SPAN_ID = "x-b3-parentSpanid";
	protected static final String SAMPLED = "x-b3-sampled";
	protected static final String FLAGS = "x-b3-flags";

	@Override
	public void inject(CloudTraceSpanContext spanContext, TextMap carrier) {
		if (spanContext == null) {
			return;
		}

		carrier.put(TRACE_ID, spanContext.toTraceId());
		carrier.put(SPAN_ID, spanContext.toSpanId());
		spanContext.parentSpanId().ifPresent(v -> carrier.put(PARENT_SPAN_ID, Long.toHexString(v).toLowerCase()));
		carrier.put(SAMPLED, spanContext.sampled() ? "1" : "0");
		// carrier.put(FLAGS, spanContext.flags());
	}

	@Override
	public CloudTraceSpanContext extract(TextMap carrier) {
		String traceId = null;
		String spanId = null;
		String parentSpanId = null;
		String sampled = null;

		for (Map.Entry<String, String> e : carrier) {
			switch (e.getKey().toLowerCase()) {
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
			return null;
		}

		var builder = CloudTraceSpanContext.builder(traceId, spanId).sampled("1".equals(sampled));
		if (parentSpanId != null) {
			builder.parentSpanId(parentSpanId);
		}
		return builder.build();
	}
}
