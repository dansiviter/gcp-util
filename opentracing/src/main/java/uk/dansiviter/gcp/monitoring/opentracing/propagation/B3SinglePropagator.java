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

import java.util.OptionalLong;

import io.opentracing.propagation.TextMap;
import uk.dansiviter.gcp.monitoring.opentracing.CloudTraceSpanContext;

/**
 * @author Daniel Siviter
 * @since v1.0 [15 Dec 2019]
 */
public class B3SinglePropagator implements TextMapPropagator {
	protected static final String B3 = "B3";

	/**
	 *
	 * @return
	 */
	protected String header() {
		return B3;
	}

	@Override
	public void inject(CloudTraceSpanContext spanContext, TextMap carrier) {
		if (spanContext == null) {
			return;
		}
		var buf = new StringBuilder(spanContext.toTraceId())
			.append('-')
			.append(spanContext.toSpanId())
			.append('-')
			.append(spanContext.sampled() ? "1" : "0");
		carrier.put(header(), buf.toString());
	}

	@Override
	public CloudTraceSpanContext extract(TextMap carrier) {
		String value = null;

		for (var e : carrier) {
			if (header().equalsIgnoreCase(e.getKey())) {
				value = e.getValue();
				break;
			}
		}

		if (value == null) {
			return null;
		}

		final String[] tokens = value.split("-");
		if (tokens.length == 1 && "0".equals(tokens[0])) {
			return CloudTraceSpanContext.builder(OptionalLong.empty(), 0, 0).build();
		}
		var builder = CloudTraceSpanContext.builder(tokens[0], tokens[1]);
		if (tokens.length == 3) {
			builder.sampled("1".equals(tokens[2]));
		}
		return builder.build();
	}
}
