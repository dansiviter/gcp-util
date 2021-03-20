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
package uk.dansiviter.gcp.opentracing;

import static io.opentracing.References.CHILD_OF;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tag;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class CloudTraceSpan implements Span {
	private final Map<String, String> baggage = new HashMap<>();
	private final List<Log> logs = new LinkedList<>();

	private final CloudTracer tracer;
	private final List<Ref> references;
	private final Map<String, Object> tags;
	private final CloudTraceSpanContext ctx;

	private String operationName;
	private long startUs;
	private long finishUs;

	private CloudTraceSpan(CloudTracer tracer, Builder builder) {
		this.tracer = tracer;
		this.operationName = builder.operationName;
		this.references = List.copyOf(builder.references);
		this.tags = new HashMap<>(builder.tags);
		this.startUs = builder.startUs;

		final Optional<CloudTraceSpanContext> parentCtx = parentContext();
		this.ctx = CloudTraceSpanContext.builder(parentCtx, tracer.sampler()).build();
	}

	private Optional<CloudTraceSpanContext> parentContext() {
		if (this.references.isEmpty()) {
			return Optional.empty();
		}

		var parent = references.get(0);
		for (Ref ref: references) {
			if (CHILD_OF.equals(ref.type) && !CHILD_OF.equals(parent.type)) {
				parent = ref;
				break;
			}
		}
		return Optional.ofNullable(parent).map(Ref::ctx);
	}

	@Override
	public CloudTraceSpanContext context() {
		return this.ctx;
	}

	@Override
	public Span setTag(String key, String value) {
		this.tags.put(key, value);
		return this;
	}

	@Override
	public Span setTag(String key, boolean value) {
		this.tags.put(key, value);
		return this;
	}

	@Override
	public Span setTag(String key, Number value) {
		this.tags.put(key, value);
		return this;
	}

	@Override
	public <T> Span setTag(Tag<T> tag, T value) {
		this.tags.put(tag.getKey(), value);
		return this;
	}

	@Override
	public Span log(Map<String, ?> fields) {
		if (fields.containsKey("event")) {
			this.logs.add(new Log(nowUs(), Optional.of(fields.get("event").toString()), fields));
		} else {
			this.logs.add(new Log(nowUs(), Optional.empty(), fields));
		}
		return this;
	}

	@Override
	public Span log(long timestampMicroseconds, Map<String, ?> fields) {
		this.logs.add(new Log(timestampMicroseconds, Optional.empty(), fields));
		return this;
	}

	@Override
	public Span log(String event) {
		this.logs.add(new Log(nowUs(), Optional.of(event), emptyMap()));
		return this;
	}

	@Override
	public Span log(long timestampMicroseconds, String event) {
		this.logs.add(new Log(timestampMicroseconds, Optional.of(event), emptyMap()));
		return this;
	}

	@Override
	public Span setBaggageItem(String key, String value) {
		this.baggage.put(key, value);
		return this;
	}

	@Override
	public String getBaggageItem(String key) {
		return this.baggage.get(key);
	}

	@Override
	public Span setOperationName(String operationName) {
		this.operationName = operationName;
		return this;
	}

	@Override
	public void finish() {
		finish(nowUs());
	}

	@Override
	public void finish(long finishMicros) {
		this.finishUs = finishMicros;
		this.tracer.persist(this);
	}

	/**
	 * @return the operationName
	 */
	String operationName() {
		return this.operationName;
	}

	/**
	 * @return the startUs
	 */
	long startUs() {
		return startUs;
	}

	/**
	 * @return the finishUs
	 */
	long finishUs() {
		return finishUs;
	}

	/**
	 * @return the tags
	 */
	Map<String, Object> tags() {
		return tags;
	}

	/**
	 * @return the logs
	 */
	List<Log> logs() {
		return logs;
	}

	/**
	 *
	 * @return
	 */


	// --- Static Methods ---

	/**
	 *
	 * @param operationName
	 * @param tracer
	 * @return
	 */
	static Tracer.SpanBuilder builder(@Nonnull String operationName, @Nonnull CloudTracer tracer) {
		return new Builder(operationName, tracer);
	}

	/**
	 *
	 * @param dateTime
	 * @return
	 */
	private static long us(@Nonnull ZonedDateTime dateTime) {
		return MICROSECONDS.convert(dateTime.toEpochSecond(), SECONDS)
				+ MICROSECONDS.convert(dateTime.getNano(), NANOSECONDS);
	}

	private static long nowUs() {
		return us(ZonedDateTime.now());
	}

	// --- Inner Classes ---

	/**
	 *
	 */
	private static class Builder implements Tracer.SpanBuilder {
		private final String operationName;
		private final CloudTracer tracer;
		private final Map<String, Object> tags = new HashMap<>();

		private List<Ref> references = Collections.emptyList();
		private boolean ignoreActiveSpan = false;
		private long startUs;

		private Builder(@Nonnull String operationName, @Nonnull CloudTracer tracer) {
			this.operationName = operationName;
			this.tracer = tracer;
		}

		@Override
		public SpanBuilder asChildOf(SpanContext parent) {
			addReference(References.CHILD_OF, parent);
			return this;
		}

		@Override
		public SpanBuilder asChildOf(Span parent) {
			return asChildOf(parent.context());
		}

		@Override
		public SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
			var ref = new Ref(referenceType, (CloudTraceSpanContext) referencedContext);
			if (references.isEmpty()) {
				references = Collections.singletonList(ref);
			} else {
				if (references.size() == 1) {
					references = new ArrayList<>(references);
				}
				references.add(ref);
			}
			return this;
		}

		@Override
		public SpanBuilder ignoreActiveSpan() {
			this.ignoreActiveSpan = true;
			return this;
		}

		@Override
		public SpanBuilder withTag(String key, String value) {
			this.tags.put(key, value);
			return this;
		}

		@Override
		public SpanBuilder withTag(String key, boolean value) {
			this.tags.put(key, value);
			return this;
		}

		@Override
		public SpanBuilder withTag(String key, Number value) {
			this.tags.put(key, value);
			return this;
		}

		@Override
		public <T> SpanBuilder withTag(Tag<T> tag, T value) {
			this.tags.put(tag.getKey(), value);
			return this;
		}

		@Override
		public SpanBuilder withStartTimestamp(long microseconds) {
			this.startUs = microseconds;
			return this;
		}

		@Override
		public Span start() {
			var scopeManager = this.tracer.scopeManager();
			if (this.references.isEmpty() && !ignoreActiveSpan && !isNull(scopeManager.activeSpan())) {
				asChildOf(scopeManager.activeSpan());
			}

			if (this.startUs == 0) {
				this.startUs = nowUs();
			}

			return new CloudTraceSpan(tracer, this);
		}
	}

	static class Ref {
		final String type;
		final CloudTraceSpanContext ctx;

		Ref(String type, CloudTraceSpanContext ctx) {
			this.type = type;
			this.ctx = ctx;
		}

		CloudTraceSpanContext ctx() {
			return this.ctx;
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
