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
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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
	private final List<CloudTraceEvent> events = new LinkedList<>();

	private final CloudTracer tracer;
	private final List<CloudTraceLink> links;
	private Attributes attrs;
	private final SpanContext ctx;
	private final Kind kind;

	private String name;
	private long startNs;
	private long endNs;
	private StatusCode statusCode;
	private String statusDescription;

	private CloudTraceSpan(CloudTracer tracer, Builder builder) {
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
			Attributes.Builder b = Attributes.builder(this.attrs);
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
		return addEvent(new CloudTraceEvent(name, -1, Attributes.empty()));
	}

	@Override
	public Span addEvent(String name, Attributes attributes) {
		return addEvent(new CloudTraceEvent(name, -1, attributes));
	}

	@Override
	public Span addEvent(String name, Attributes attributes, long timestamp) {
		return addEvent(new CloudTraceEvent(name, timestamp, attributes));
	}

	@Override
	public Span addEvent(String name, long timestamp) {
		return addEvent(new CloudTraceEvent(name, timestamp, Attributes.empty()));
	}

    @Override
	public Span setStatus(StatusCode canonicalCode) {
		return setStatus(canonicalCode, null);
	}

	@Override
	public Span setStatus(StatusCode canonicalCode, String description) {
		this.statusCode = canonicalCode;
		this.statusDescription = description;
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
		return this.ctx.isSampled() && this.endNs > 0;
	}

	@Override
	public void end(long timestamp) {
		if (!isRecording()) {
			throw new IllegalStateException("Span already ended!");
		}
		this.endNs = timestamp;
		this.tracer.persist(this);
	}

	@Override
	public void end() {
		end(nowNs());
	}

	String name() {
		return this.name;
	}

	List<CloudTraceLink> links() {
		return this.links;
	}

	Kind kind() {
		return this.kind;
	}

	StatusCode statusCode() {
		return this.statusCode;
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
	long endNs() {
		return this.endNs;
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
	List<CloudTraceEvent> events() {
		return events;
	}

	// --- Static Methods ---

	/**
	 *
	 * @param operationName
	 * @param tracer
	 * @return
	 */
	static Span.Builder builder(@Nonnull String operationName, @Nonnull CloudTracer tracer) {
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
		private final CloudTracer tracer;
		private final Attributes.Builder attrs = Attributes.builder();

		private Optional<SpanContext> parent = Optional.empty();
		private List<CloudTraceLink> links = Collections.emptyList();
		private long startNs;
		private Optional<Kind> spanKind = Optional.empty();

		private Builder(@Nonnull String name, @Nonnull CloudTracer tracer) {
			this.name = name;
			this.tracer = tracer;
		}

		@Override
		public Span.Builder setParent(Context parent) {
			var span = Span.fromContext(parent);
			this.parent = Optional.ofNullable(span != null ? span.getSpanContext() : null);
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
			if (this.parent.isEmpty() && !isNull(Span.current())) {
				this.parent = Optional.of(Span.current().getSpanContext());
			}

			if (this.startNs == 0) {
				this.startNs = nowNs();
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
		final long timestamp;
		final Attributes attrs;

		CloudTraceEvent(@Nonnull String name, long timestamp, @Nonnull Attributes attrs) {
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
