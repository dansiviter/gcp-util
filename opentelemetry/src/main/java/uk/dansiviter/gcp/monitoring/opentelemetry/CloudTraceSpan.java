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
package uk.dansiviter.gcp.monitoring.opentelemetry;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Feb 2020]
 */
public class CloudTraceSpan implements Span {

	private final CloudTracer tracer;
	private final SpanContext ctx;

	final List<CloudTraceEvent> events = new LinkedList<>();
	final List<CloudTraceLink> links;
	final SpanKind kind;
	Attributes attrs;
	String name;
	Instant start;
	Instant end;
	Optional<StatusCode> statusCode = Optional.empty();
	Optional<String> statusDescription = Optional.empty();

	private CloudTraceSpan(CloudTracer tracer, Builder builder) {
		this.tracer = tracer;
		this.name = requireNonNull(builder.name);
		this.links = List.copyOf(builder.links);
		this.attrs = builder.attrs.build();
		this.start = builder.start;
		this.kind = builder.spanKind.orElse(SpanKind.INTERNAL);

		final Optional<SpanContext> parentCtx = builder.parent;
		final boolean sampled = tracer.sampler().test(parentCtx);
		this.ctx = SpanContext.create(parentCtx.map(SpanContext::getTraceId).orElse(Factory.randomTraceId()),
				Factory.randomSpanId(), sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
				TraceState.getDefault());
	}

	@Override
	public SpanContext getSpanContext() {
		return this.ctx;
	}

	@Override
	public Span updateName(String name) {
		this.name = name;
		return this;
	}

	@Override
	public Span setAttribute(String key, String value) {
		return setAttribute(AttributeKey.stringKey(key), value);
	}

	@Override
	public Span setAttribute(String key, boolean value) {
		return setAttribute(booleanKey(key), value);
	}

	@Override
	public Span setAttribute(String key, double value) {
		return setAttribute(doubleKey(key), value);
	}

	@Override
	public Span setAttribute(String key, long value) {
		return setAttribute(longKey(key), value);
	}

	@Override
	public <T> Span setAttribute(AttributeKey<T> key, T value) {
		if (isRecording()) {
			AttributesBuilder b = Attributes.builder().putAll(this.attrs);
			b.put(key, value);
			this.attrs = b.build();
		}
		return this;
	}

	private Span addEvent(CloudTraceEvent event) {
		if (isRecording()) {
			this.events.add(event);
		}
		return this;
	}

	@Override
	public Span addEvent(String name) {
		return addEvent(name, Attributes.empty());
	}

	@Override
	public Span addEvent(String name, long timestamp, TimeUnit unit) {
		return addEvent(name, toInstant(timestamp, unit));
	}

	@Override
	public Span addEvent(String name, Instant timestamp) {
		return addEvent(name, Attributes.empty(), timestamp);
	}

	@Override
	public Span addEvent(String name, Attributes attributes) {
		return addEvent(name, attributes, Instant.now());
	}

	@Override
	public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
		return addEvent(name, attributes, Instant.ofEpochMilli(unit.toMillis(timestamp)));
	}

	@Override
	public Span addEvent(String name, Attributes attributes, Instant timestamp) {
	 	return addEvent(new CloudTraceEvent(name, timestamp, attributes));
	}

    @Override
	public Span setStatus(StatusCode code) {
		return setStatus(code, null);
	}

	@Override
	public Span setStatus(StatusCode code, String description) {
		this.statusCode = Optional.ofNullable(code);
		this.statusDescription = Optional.ofNullable(description);
		return this;
	}

	@Override
	public Span recordException(Throwable exception) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Span recordException(Throwable exception, Attributes additionalAttributes) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isRecording() {
		return this.ctx.isSampled() && this.end == null;
	}

	@Override
	public void end(long timestamp, TimeUnit unit) {
		end(toInstant(timestamp, unit));
	}

	@Override
	public void end(Instant timestamp) {
		if (!isRecording()) {
			throw new IllegalStateException("Span already ended!");
		}
		this.end = timestamp;
		this.tracer.persist(this);
	}

	@Override
	public void end() {
		end(Instant.now());
	}


	// --- Static Methods ---

	/**
	 *
	 * @param operationName
	 * @param tracer
	 * @return
	 */
	static SpanBuilder builder(@Nonnull String operationName, @Nonnull CloudTracer tracer) {
		return new Builder(operationName, tracer);
	}

	private static Instant toInstant(long timestamp, TimeUnit unit) {
		return Instant.ofEpochSecond(unit.toSeconds(timestamp), timestamp % 1_000_000_000);
	}


	// --- Inner Classes ---

	/**
	 *
	 */
	private static class Builder implements SpanBuilder {
		private final String name;
		private final CloudTracer tracer;
		private final AttributesBuilder attrs = Attributes.builder();

		private Optional<SpanContext> parent = Optional.empty();
		private List<CloudTraceLink> links = Collections.emptyList();
		private Instant start;
		private Optional<SpanKind> spanKind = Optional.empty();

		private Builder(@Nonnull String name, @Nonnull CloudTracer tracer) {
			this.name = name;
			this.tracer = tracer;
		}

		@Override
		public SpanBuilder setParent(Context parent) {
			var span = Span.fromContext(parent);
			this.parent = Optional.ofNullable(span != null ? span.getSpanContext() : null);
			return this;
		}

		@Override
		public SpanBuilder setNoParent() {
			this.parent = Optional.empty();
			return this;
		}

		@Override
		public SpanBuilder addLink(SpanContext spanContext) {
			return addLink(spanContext, null);
		}

		@Override
		public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
			var wrapped = new CloudTraceLink(spanContext, attributes);
			if (this.links.isEmpty()) {
				this.links = Collections.singletonList(wrapped);
			} else {
				if (this.links.size() == 1) {
					this.links = new LinkedList<>(this.links);
				}
				this.links.add(wrapped);
			}
			return this;
		}

		@Override
		public Builder setAttribute(String key, String value) {
			return setAttribute(stringKey(key), value);
		}

		@Override
		public Builder setAttribute(String key, long value) {
			return setAttribute(AttributeKey.longKey(key), value);
		}

		@Override
		public Builder setAttribute(String key, double value) {
			return setAttribute(doubleKey(key), value);
		}

		@Override
		public Builder setAttribute(String key, boolean value) {
			return setAttribute(booleanKey(key), value);
		}

		@Override
		public <T> Builder setAttribute(AttributeKey<T> key, T value) {
			this.attrs.put(key, value);
			return this;
		}

		@Override
		public SpanBuilder setSpanKind(SpanKind spanKind) {
			this.spanKind = Optional.of(spanKind);
			return this;
		}

		@Override
		public SpanBuilder setStartTimestamp(long timestamp, TimeUnit unit) {
			return setStartTimestamp(toInstant(timestamp, unit));
		}

		@Override
		public SpanBuilder setStartTimestamp(Instant timestamp) {
			this.start = timestamp;
			return this;
		}

		@Override
		public Span startSpan() {
			if (this.parent.isEmpty() && !isNull(Span.current())) {
				this.parent = Optional.of(Span.current().getSpanContext());
			}

			if (this.start == null) {
				this.start = Instant.now();
			}

			return new CloudTraceSpan(tracer, this);
		}
	}

	static class CloudTraceLink {
		final SpanContext ctx;
		final Attributes attrs;

		CloudTraceLink(@Nonnull SpanContext ctx, @Nonnull Attributes attrs) {
			this.ctx = requireNonNull(ctx);
			this.attrs = requireNonNull(attrs);
		}
	}

	static class CloudTraceEvent {
		final String name;
		final Instant timestamp;
		final Attributes attrs;

		CloudTraceEvent(@Nonnull String name, @Nonnull Instant timestamp, @Nonnull Attributes attrs) {
			this.name = requireNonNull(name);
			this.timestamp = requireNonNull(timestamp);
			this.attrs = requireNonNull(attrs);
		}
	}

	/**
	 *
	 */
	static class Log {
		final long timeUs;
		final Optional<String> event;
		final Map<String, Object> fields;

		@SuppressWarnings({ "unchecked", "rawtypes" })
		Log(@Nonnull long timeUs, @Nonnull Optional<String> event, @Nonnull Map<String, ?> fields) {
			this.timeUs = timeUs;
			this.event = event;
			this.fields = (Map) fields;
		}
	}
}
