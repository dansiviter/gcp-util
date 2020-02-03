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
package uk.dansiviter.stackdriver.log.opentracing;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.ServiceOptions;
import com.google.cloud.logging.LogEntry.Builder;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.util.GlobalTracer;
import uk.dansiviter.stackdriver.log.Entry;
import uk.dansiviter.stackdriver.log.EntryDecorator;

/**
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class Decorator implements EntryDecorator {
	protected static final String TRACE_ID_NAME = "X-B3-TraceId";
	protected static final String SPAN_ID_NAME = "X-B3-SpanId";
	protected static final String SAMPLED_NAME = "X-B3-Sampled";
	protected static final String B3 = "B3";
	protected static final String TRACEPARENT = "traceparent";

	private final String prefix;
	private final Supplier<Tracer> tracer;

	/**
	 * Creates a using {@link GlobalTracer#get()}
	 */
	public Decorator() {
		this(GlobalTracer::get);
	}

	/**
	 * @param tracer the tracer supplier.
	 */
	public Decorator(@Nonnull Supplier<Tracer> tracer) {
		this(tracer, Optional.of(ServiceOptions.getDefaultProjectId()));
	}

	/**
	 *
	 * @param tracer the tracer supplier.
	 * @param projectId the project identifier.
	 */
	public Decorator(@Nonnull Supplier<Tracer> tracer, Optional<String> projectId) {
		this.prefix = String.format("projects/%s/traces/", projectId.orElse(""));
		this.tracer = requireNonNull(tracer);
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		final Tracer tracer = this.tracer.get();
		final Span span = tracer.activeSpan();
		if (span == null) {
			return;
		}
		tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapAdapter(headers));

		// try B3 Multi
		headers.computeIfPresent(TRACE_ID_NAME, (k, v) -> {
			b.setTrace(this.prefix.concat(v));
			b.setSpanId(headers.get(SPAN_ID_NAME));
			sampled(headers).ifPresent(b::setTraceSampled);
			return v;
		});

		// try B3 Single
		headers.computeIfPresent(B3, (k, v) -> parseB3Single(k, v, b));

		// try W3C Span
		headers.computeIfPresent(TRACEPARENT, (k, v) -> parseW3cSingle(k, v, b));
	}

	/**
	 *
	 * @param key
	 * @param value
	 * @param b
	 * @return
	 */
	private static String parseB3Single(String key, String value, Builder b) {
		final String[] tokens = value.split("-");
		if (tokens.length < 2) {
			return value;
		}

		b.setTrace(tokens[0]);
		b.setSpanId(tokens[1]);
		if (tokens.length == 3) {
			b.setTraceSampled("1".equals(tokens[2]));
		}
		return value;
	}

	/**
	 *
	 * @param key
	 * @param value
	 * @param b
	 * @return
	 */
	private static String parseW3cSingle(String key, String value, Builder b) {
		final String[] tokens = value.split("-");
		b.setTrace(tokens[1]);
		b.setSpanId(tokens[2]);
		if (tokens.length == 4) {
			final byte flags = Byte.parseByte(tokens[3], 16);
			b.setTraceSampled(flag(flags, (byte) 1));
		}
		return value;
	}

	/**
	 *
	 * @param headers
	 * @return
	 */
	private static Optional<Boolean> sampled(Map<String, String> headers) {
		return get(headers, SAMPLED_NAME).map(s -> "1".equals(s));
	}

	/**
	 *
	 * @param headers
	 * @param key
	 * @return
	 */
	private static Optional<String> get(Map<String, String> headers, String key) {
		final String value = headers.get(key);
		return Optional.ofNullable(value);
	}

	/**
	 *
	 * @param flags
	 * @param flag
	 * @return
	 */
	private static boolean flag(byte flags, byte flag) {
		return (flags & flag) == flag;
	}
}
