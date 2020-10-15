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
package uk.dansiviter.stackdriver.opentelemetry;

import static io.opentelemetry.common.AttributeKey.booleanKey;
import static io.opentelemetry.common.AttributeKey.doubleKey;
import static io.opentelemetry.common.AttributeKey.longKey;
import static io.opentelemetry.common.AttributeKey.stringKey;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import io.grpc.Context;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.StatusCanonicalCode;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.TracingContextUtils;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Feb 2020]
 */
public class StackdriverSpan implements Span {
	private final List<StackdriverEvent> events = new LinkedList<>();

	private final StackdriverTracer tracer;
	private final List<StackdriverLink> links;
	private Attributes attrs;
	private final SpanContext ctx;
	private final Kind kind;

	private String name;
	private long startNs;
	private StatusCanonicalCode statusCanonicalCode;
	private String statusDescription;
	private EndSpanOptions endOptions;

	private StackdriverSpan(StackdriverTracer tracer, Builder builder) {
		this.tracer = tracer;
		this.name = requireNonNull(builder.name);
		this.links = List.copyOf(builder.links);
		this.attrs = builder.attrs.build();
		this.startNs = builder.startNs;
		this.kind = builder.spanKind.orElse(Kind.INTERNAL);

		final Optional<SpanContext> parentCtx = builder.parent;
		final boolean sampled = tracer.sampler().test(parentCtx);
		this.ctx = SpanContext.create(parentCtx.map(SpanContext::getTraceIdAsHexString).orElse(Factory.randomTraceId()),
				Factory.randomSpanId(), sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
				TraceState.getDefault());
	}

	@Override
	public SpanContext getContext() {
		return this.ctx;
	}

	@Override
	public void setAttribute(String key, String value) {
		setAttribute(AttributeKey.stringKey(key), value);
	}

	@Override
	public void setAttribute(String key, boolean value) {
		setAttribute(booleanKey(key), value);
	}

	@Override
	public void setAttribute(String key, double value) {
		setAttribute(doubleKey(key), value);
	}

	@Override
	public void setAttribute(String key, long value) {
		setAttribute(longKey(key), value);
	}

	@Override
	public <T> void setAttribute(AttributeKey<T> key, T value) {
		if (isRecording()) {
			Attributes.Builder b = Attributes.newBuilder();
			this.attrs.forEach(b::setAttribute);
			b.setAttribute(key, value);
			this.attrs = b.build();
		}
	}

	public void addEvent(StackdriverEvent event) {
		if (isRecording()) {
			this.events.add(event);
		}
	}

	@Override
	public void addEvent(String name) {
		addEvent(new StackdriverEvent(name, -1, Attributes.empty()));
	}

	@Override
	public void addEvent(String name, Attributes attributes) {
		addEvent(new StackdriverEvent(name, -1, attributes));
	}

	@Override
	public void addEvent(String name, Attributes attributes, long timestamp) {
		addEvent(new StackdriverEvent(name, timestamp, attributes));
	}

	@Override
	public void addEvent(String name, long timestamp) {
		addEvent(new StackdriverEvent(name, timestamp, Attributes.empty()));
	}

    @Override
	public void setStatus(StatusCanonicalCode canonicalCode) {
		setStatus(canonicalCode, null);
	}

	@Override
	public void setStatus(StatusCanonicalCode canonicalCode, String description) {
		this.statusCanonicalCode = canonicalCode;
		this.statusDescription = description;
	}

	@Override
	public void recordException(Throwable exception) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void recordException(Throwable exception, Attributes additionalAttributes) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateName(String name) {
		this.name = name;
	}

	@Override
	public boolean isRecording() {
		return this.ctx.isSampled() && this.endOptions == null;
	}

	@Override
	public void end(EndSpanOptions endOptions) {
		this.endOptions = endOptions;
		this.tracer.persist(this);
	}

	@Override
	public void end() {
		end(EndSpanOptions.builder().setEndTimestamp(nowNs()).build());
	}

	String name() {
		return this.name;
	}

	List<StackdriverLink> links() {
		return this.links;
	}

	Kind kind() {
		return this.kind;
	}

	StatusCanonicalCode statusCanonicalCode() {
		return this.statusCanonicalCode;
	}

	String statusDesciption() {
		return this.statusDescription;
	}

	/**
	 * @return the startUs
	 */
	long startNs() {
		return startNs;
	}

	/**
	 * @return the finishUs
	 */
	EndSpanOptions endOptions() {
		return this.endOptions;
	}

	/**
	 * @return the tags
	 */
	Attributes attributes() {
		return attrs;
	}

	/**
	 * @return the logs
	 */
	List<StackdriverEvent> events() {
		return events;
	}

	// --- Static Methods ---

	/**
	 *
	 * @param operationName
	 * @param tracer
	 * @return
	 */
	static Span.Builder builder(@Nonnull String operationName, @Nonnull StackdriverTracer tracer) {
		return new Builder(operationName, tracer);
	}

	/**
	 *
	 * @param dateTime
	 * @return
	 */
	private static long ns(@Nonnull ZonedDateTime dateTime) {
		return NANOSECONDS.convert(dateTime.toEpochSecond(), SECONDS) + dateTime.getNano();
	}

	private static long nowNs() {
		return ns(ZonedDateTime.now());
	}

	// --- Inner Classes ---

	/**
	 *
	 */
	private static class Builder implements Span.Builder {
		private final String name;
		private final StackdriverTracer tracer;
		private final Attributes.Builder attrs = Attributes.newBuilder();

		private Optional<SpanContext> parent = Optional.empty();
		private List<StackdriverLink> links = Collections.emptyList();
		private long startNs;
		private Optional<Kind> spanKind = Optional.empty();

		private Builder(@Nonnull String name, @Nonnull StackdriverTracer tracer) {
			this.name = name;
			this.tracer = tracer;
		}

		@Override
		public Span.Builder setParent(Context parent) {
			Span span = TracingContextUtils.getSpan(parent);
			this.parent = Optional.ofNullable(span != null ? span.getContext() : null);
			return this;
		}

		@Override
		public Span.Builder setNoParent() {
			this.parent = Optional.empty();
			return this;
		}

		@Override
		public Span.Builder addLink(SpanContext spanContext) {
			return addLink(spanContext, null);
		}

		@Override
		public Span.Builder addLink(SpanContext spanContext, Attributes attributes) {
			final StackdriverLink wrapped = new StackdriverLink(spanContext, attributes);
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
			this.attrs.setAttribute(key, value);
			return this;
		}

		@Override
		public Span.Builder setSpanKind(Kind spanKind) {
			this.spanKind = Optional.of(spanKind);
			return this;
		}

		@Override
		public Span.Builder setStartTimestamp(long startTimestamp) {
			this.startNs = startTimestamp;
			return this;
		}

		@Override
		public Span startSpan() {
			if (this.parent.isEmpty() && !isNull(this.tracer.getCurrentSpan())) {
				this.parent = Optional.of(this.tracer.getCurrentSpan().getContext());
			}

			if (this.startNs == 0) {
				this.startNs = nowNs();
			}

			return new StackdriverSpan(tracer, this);
		}
	}

	static class StackdriverLink {
		final SpanContext ctx;
		final Attributes attrs;

		StackdriverLink(@Nonnull SpanContext ctx, @Nonnull Attributes attrs) {
			this.ctx = requireNonNull(ctx);
			this.attrs = requireNonNull(attrs);
		}
	}

	static class StackdriverEvent {
		final String name;
		final long timestamp;
		final Attributes attrs;

		StackdriverEvent(@Nonnull String name, long timestamp, @Nonnull Attributes attrs) {
			this.name = requireNonNull(name);
			this.timestamp = timestamp;
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
