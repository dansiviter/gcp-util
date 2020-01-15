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
package uk.dansiviter.stackdriver.opentracing.propagation;

import java.util.Map;

import io.opentracing.propagation.TextMap;
import uk.dansiviter.stackdriver.opentracing.StackdriverSpanContext;

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
	public void inject(StackdriverSpanContext spanContext, TextMap carrier) {
		if (spanContext == null) {
			return;
		}
		final StringBuilder buf = new StringBuilder(spanContext.traceId())
			.append('-')
			.append(spanContext.spanIdAsString())
			.append('-')
			.append(spanContext.sampled() ? "1" : "0");
		carrier.put(header(), buf.toString());
	}

	@Override
	public StackdriverSpanContext extract(TextMap carrier) {
		String value = null;

		for (Map.Entry<String, String> e : carrier) {
			if (header().equalsIgnoreCase(e.getKey())) {
				value = e.getValue();
				break;
			}
		}

		if (value == null) {
			return null;
		}

		final String[] tokens = value.split("-");
		final StackdriverSpanContext.Builder builder = StackdriverSpanContext.builder(tokens[0], tokens[1]);
		if (tokens.length == 3) {
			builder.sampled("1".equals(tokens[2]));
		}
		return builder.build();
	}
}
