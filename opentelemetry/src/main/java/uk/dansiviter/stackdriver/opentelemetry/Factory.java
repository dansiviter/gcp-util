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

import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static uk.dansiviter.stackdriver.Util.threadLocal;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.devtools.cloudtrace.v2.AttributeValue;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.devtools.cloudtrace.v2.Span.Attributes;
import com.google.devtools.cloudtrace.v2.Span.Link;
import com.google.devtools.cloudtrace.v2.Span.Links;
import com.google.devtools.cloudtrace.v2.Span.TimeEvent;
import com.google.devtools.cloudtrace.v2.Span.TimeEvents;
import com.google.devtools.cloudtrace.v2.SpanName;
import com.google.devtools.cloudtrace.v2.TruncatableString;
import com.google.protobuf.Timestamp;
import com.google.rpc.Status;

import io.opentelemetry.common.AttributeConsumer;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.StatusCanonicalCode;
import io.opentelemetry.trace.TraceId;
import uk.dansiviter.stackdriver.ResourceType;
import uk.dansiviter.stackdriver.ResourceType.Label;
import uk.dansiviter.stackdriver.opentelemetry.StackdriverSpan.StackdriverEvent;
import uk.dansiviter.stackdriver.opentelemetry.StackdriverSpan.StackdriverLink;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Feb 2020]
 */
public class Factory {
	private static final ThreadLocal<SpanName.Builder> SPAN_NAME_BUILDER = threadLocal(SpanName::newBuilder, b -> b);
	private static final ThreadLocal<Span.Builder> SPAN_BUILDER = threadLocal(Span::newBuilder, Span.Builder::clear);
	private static final ThreadLocal<TruncatableString.Builder> STRING_BUILDER = threadLocal(
			TruncatableString::newBuilder, TruncatableString.Builder::clear);
	private static final ThreadLocal<Timestamp.Builder> TIMESTAMP_BUILDER = threadLocal(Timestamp::newBuilder,
			Timestamp.Builder::clear);
	private static final ThreadLocal<Attributes.Builder> ATTRS_BUILDER = threadLocal(Attributes::newBuilder,
			Attributes.Builder::clear);
	private static final ThreadLocal<AttributeValue.Builder> ATTR_VALUE_BUILDER = threadLocal(
			AttributeValue::newBuilder, AttributeValue.Builder::clear);
	private static final ThreadLocal<TimeEvents.Builder> TIME_EVENTS_BUILDER = threadLocal(TimeEvents::newBuilder,
			TimeEvents.Builder::clear);
	private static final ThreadLocal<TimeEvent.Builder> TIME_EVENT_BUILDER = threadLocal(TimeEvent::newBuilder,
			TimeEvent.Builder::clear);
	private static final ThreadLocal<TimeEvent.Annotation.Builder> TIME_EVENT_ANNO_BUILDER = threadLocal(
			TimeEvent.Annotation::newBuilder, TimeEvent.Annotation.Builder::clear);
	private static final SecureRandom RAND = new SecureRandom();

	private static final String AGENT_LABEL_KEY = "/agent";
	private static final AttributeValue AGENT_LABEL_VALUE = AttributeValue.newBuilder()
			.setStringValue(toTruncatableString(agent())).build();
	private static final Map<String, String> HTTP_ATTRIBUTE_MAPPING = Map.of(
		"http.host", "/http/host",
		"http.method", "/http/method",
		"http.path", "/http/path",
		"http.url", "/http/url",
		"http.route", "/http/route",
		"http.user_agent", "/http/user_agent",
		"http.status_code", "/http/status_code");

	private final MonitoredResource resource;

	private final Map<String, AttributeValue> resourceAttr;

	Factory(MonitoredResource resource) {
		this.resource = resource;
		this.resourceAttr = toAttrs(resource);
	}

	/**
	 *
	 * @param span
	 * @return
	 */
	com.google.devtools.cloudtrace.v2.Span toSpan(StackdriverSpan span) {
		final SpanContext ctx = span.getContext();
		final String spanId = ctx.getSpanIdAsHexString();
		final SpanName spanName = SPAN_NAME_BUILDER.get() // no clear method, but should override all fields anyway
				.setProject(ResourceType.get(this.resource, Label.PROJECT_ID).get())
				.setTrace(ctx.getTraceIdAsHexString()).setSpan(spanId).build();

		final Span.Builder spanBuilder = SPAN_BUILDER.get().setName(spanName.toString()).setSpanId(spanId)
				.setDisplayName(toTruncatableString(span.name()))
				.setAttributes(toAttrs(span.attributes(), this.resourceAttr))
				.setTimeEvents(toTimeEvents(span.events()))
				.setLinks(toLinks(span.links()));

		final String parentSpanId = ctx.getTraceState().get("parentSpanId");
		if (parentSpanId != null) {
			spanBuilder.setParentSpanId(parentSpanId);
		}

		status(span.statusCanonicalCode(), span.statusDesciption()).ifPresent(spanBuilder::setStatus);

		if (span.startNs() > 0) {
			spanBuilder.setStartTime(toTimestamp(span.startNs()));
		}
		if (span.endOptions() != null && span.endOptions().getEndTimestamp() > 0) {
			spanBuilder.setEndTime(toTimestamp(span.endOptions().getEndTimestamp()));
		}

		return spanBuilder.build();
	}

	private static Optional<Status> status(StatusCanonicalCode status, String description) {
		if(status == null && description == null) {
			return Optional.empty();
		}

		final Status.Builder statusBuilder = Status.newBuilder();
		if (status != null) {
			statusBuilder.setCode(status.value());
		}
		if (description != null) {
			statusBuilder.setMessage(description);
		}
		return Optional.of(statusBuilder.build());
	}

	/**
	 *
	 * @return
	 */
	private static long randomId() {
		return abs(RAND.nextLong());
	}

		/**
	 *
	 * @return
	 */
	public static String randomTraceId() {
		return TraceId.fromLongs(randomId(), randomId());
	}

	/**
	 *
	 * @return
	 */
	public static String randomSpanId() {
		return SpanId.fromLong(randomId());
	}

	/**
	 *
	 * @param charSeq
	 * @return
	 */
	private static TruncatableString toTruncatableString(@Nonnull CharSequence charSeq) {
		return STRING_BUILDER.get().setValue(charSeq.toString()).build();
	}

	/**
	 *
	 * @param microseconds
	 * @return
	 */
	private static Timestamp toTimestamp(long nanos) {
		final long remainder = nanos % 1_000_000_000;
		return TIMESTAMP_BUILDER.get()
				.setSeconds(NANOSECONDS.toSeconds(nanos))
				.setNanos((int) remainder)
				.build();
	}

	/**
	 *
	 * @param tags
	 * @param resourceAttr
	 * @return
	 */
	private static Attributes toAttrs(
			@Nonnull io.opentelemetry.common.Attributes attrs,
			@Nonnull Map<String, AttributeValue> resourceAttr)
	{
		final Attributes.Builder attributesBuilder = toAttrsBuilder(attrs);
		attributesBuilder.putAttributeMap(AGENT_LABEL_KEY, AGENT_LABEL_VALUE);
		attributesBuilder.putAllAttributeMap(resourceAttr);
		return attributesBuilder.build();
	}

	/**
	 *
	 * @param logs
	 * @return
	 */
	private static Span.TimeEvents toTimeEvents(@Nonnull List<StackdriverEvent> events) {
		final Span.TimeEvents.Builder timeEventsBuilder = TIME_EVENTS_BUILDER.get();
		events.forEach(l -> timeEventsBuilder.addTimeEvent(toTimeMessageEvent(l)));
		return timeEventsBuilder.build();
	}

	/**
	 *
	 * @param tags
	 * @return
	 */
	private static Attributes.Builder toAttrsBuilder(
			@Nonnull io.opentelemetry.common.Attributes attrs)
	{
		final Attributes.Builder attributesBuilder = ATTRS_BUILDER.get();
		attrs.forEach(new AttributeConsumer(){
			public <T> void consume(AttributeKey<T> k, T v) {
				final String key = mapKey(k.getKey());
				final AttributeValue value = toAttrValue(k, v);
				if (value != null) {
					attributesBuilder.putAttributeMap(key, value);
				}
					attributesBuilder.putAttributeMap(key, value);
				}
		});
		return attributesBuilder;
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	private static AttributeValue toAttrValue(@Nonnull String key, @Nonnull Object value) {
		final AttributeValue.Builder builder = ATTR_VALUE_BUILDER.get();
		if (value instanceof CharSequence) {
			builder.setStringValue(toTruncatableString((CharSequence) value));
		} else if (value instanceof Boolean) {
			builder.setBoolValue((Boolean) value);
		} else if (value instanceof Short || value instanceof Integer || value instanceof Long) {
			// FIXME stackdriver doesn't like status code as an integer!
			// https://issuetracker.google.com/149088139
			if ("/http/status_code".equals(key)) {
				builder.setStringValue(toTruncatableString(Objects.toString(value)));
			} else {
				builder.setIntValue(((Number) value).longValue());
			}
		} else {
			return null;
		}
		return builder.build();
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	private static <V> AttributeValue toAttrValue(@Nonnull AttributeKey<V> key, @Nonnull V value) {
		final AttributeValue.Builder builder = ATTR_VALUE_BUILDER.get();
		switch (key.getType()) {
		case BOOLEAN:
			builder.setBoolValue((Boolean) value);
			break;
		case DOUBLE:
			builder.setStringValue(toTruncatableString(Double.toString((Double) value))); // FIXME
			break;
		case LONG:
			// FIXME stackdriver doesn't like status code as an integer!
			// https://issuetracker.google.com/149088139
			if ("/http/status_code".equals(key.getKey())) {
				builder.setStringValue(toTruncatableString(Long.toString((Long) value)));
			} else {
				builder.setIntValue((Long) value);
			}
			break;
		case STRING:
				builder.setStringValue(toTruncatableString((CharSequence) value));
			break;
		default:
				// unknown type!
				return null;
			}
		return builder.build();
	}

	private static Link toLink(StackdriverLink link) {
		return Link.newBuilder()
			.setTraceId(link.ctx.getTraceIdAsHexString())
			.setSpanId(link.ctx.getSpanIdAsHexString())
			.setType(link.ctx.isRemote() ? Link.Type.PARENT_LINKED_SPAN :  Link.Type.CHILD_LINKED_SPAN)
			.setAttributes(toAttrsBuilder(link.attrs))
			.build();
	  }

	  private static Links toLinks(List<StackdriverLink> links) {
		final Links.Builder linksBuilder = Links.newBuilder();
		for (StackdriverLink link : links) {
		  linksBuilder.addLink(toLink(link));
		}
		return linksBuilder.build();
	  }

	/**
	 *
	 * @param log
	 * @return
	 */
	private static TimeEvent toTimeMessageEvent(StackdriverEvent event) {
		final TimeEvent.Builder timeEventBuilder = TIME_EVENT_BUILDER.get().setTime(toTimestamp(event.timestamp));
		final TimeEvent.Annotation.Builder annotationBuilder =
				TIME_EVENT_ANNO_BUILDER.get().setAttributes(toAttrsBuilder(event.attrs));
		timeEventBuilder.setAnnotation(annotationBuilder.build());
		return timeEventBuilder.build();
	}

	/**
	 *
	 * @param key
	 * @return
	 */
	private static String mapKey(String key) {
		return HTTP_ATTRIBUTE_MAPPING.getOrDefault(key, key);
	}

	/**
	 *
	 * @param resource
	 * @return
	 */
	private static Map<String, AttributeValue> toAttrs(MonitoredResource resource) {
		final Map<String, AttributeValue> map = new HashMap<>();
		resource.getLabels().forEach((k, v) -> map.put(k, toAttrValue(k, v)));
		return map;
	}

	private static String agent() {
		final Package pkg = Factory.class.getPackage();

		if (pkg.getImplementationVersion() == null) {
			return "stackdriver-util [development]";
		}
		return format(
			"%s:%s [%s]",
			pkg.getImplementationVendor(),
			pkg.getImplementationTitle(),
			pkg.getImplementationVersion());
	}
}
