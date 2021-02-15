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
package uk.dansiviter.gcp.monitoring.opentracing;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static uk.dansiviter.gcp.Util.threadLocal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.devtools.cloudtrace.v2.AttributeValue;
import com.google.devtools.cloudtrace.v2.Span;
import com.google.devtools.cloudtrace.v2.Span.Attributes;
import com.google.devtools.cloudtrace.v2.Span.TimeEvent;
import com.google.devtools.cloudtrace.v2.Span.TimeEvents;
import com.google.devtools.cloudtrace.v2.SpanName;
import com.google.devtools.cloudtrace.v2.TruncatableString;
import com.google.protobuf.Timestamp;

import uk.dansiviter.gcp.ResourceType;
import uk.dansiviter.gcp.ResourceType.Label;
import uk.dansiviter.gcp.monitoring.opentracing.CloudTraceSpan.Log;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class Factory {
	 // SpanName.Builder has no clear method, but should override all fields anyway
	private static final ThreadLocal<SpanName.Builder> SPAN_NAME_BUILDER =
			threadLocal(SpanName::newBuilder, b -> b);
	private static final ThreadLocal<Span.Builder> SPAN_BUILDER =
			threadLocal(Span::newBuilder, Span.Builder::clear);
	private static final ThreadLocal<TruncatableString.Builder> STRING_BUILDER =
			threadLocal(TruncatableString::newBuilder, TruncatableString.Builder::clear);
	private static final ThreadLocal<Timestamp.Builder> TIMESTAMP_BUILDER =
			threadLocal(Timestamp::newBuilder, Timestamp.Builder::clear);
	private static final ThreadLocal<Attributes.Builder> ATTRS_BUILDER =
			threadLocal(Attributes::newBuilder, Attributes.Builder::clear);
	private static final ThreadLocal<AttributeValue.Builder> ATTR_VALUE_BUILDER =
			threadLocal(AttributeValue::newBuilder, AttributeValue.Builder::clear);
	private static final ThreadLocal<TimeEvents.Builder> TIME_EVENTS_BUILDER =
			threadLocal(TimeEvents::newBuilder, TimeEvents.Builder::clear);
	private static final ThreadLocal<TimeEvent.Builder> TIME_EVENT_BUILDER =
			threadLocal(TimeEvent::newBuilder, TimeEvent.Builder::clear);
	private static final ThreadLocal<TimeEvent.Annotation.Builder> TIME_EVENT_ANNO_BUILDER =
			threadLocal(TimeEvent.Annotation::newBuilder, TimeEvent.Annotation.Builder::clear);

	private static final String AGENT_LABEL_KEY = "/agent";
	private static final AttributeValue AGENT_LABEL_VALUE = AttributeValue.newBuilder()
			.setStringValue(toTruncatableString(agent())).build();
	private static final Map<String, String> HTTP_ATTRIBUTE_MAPPING = Map.of(
		"http.host",		"/http/host",
		"http.method",		"/http/method",
		"http.path",		"/http/path",
		"http.url",			"/http/url",
		"http.route",		"/http/route",
		"http.user_agent",	"/http/user_agent",
		"http.status_code",	"/http/status_code");

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
	com.google.devtools.cloudtrace.v2.Span toSpan(CloudTraceSpan span) {
		var spanId = span.context().toSpanId();
		var spanName = SPAN_NAME_BUILDER.get()
            .setProject(ResourceType.get(this.resource, Label.PROJECT_ID).get())
            .setTrace(span.context().toTraceId())
            .setSpan(spanId)
			.build();

		var spanBuilder = SPAN_BUILDER.get()
				.setName(spanName.toString())
				.setSpanId(spanId)
				.setDisplayName(toTruncatableString(span.operationName()))
				.setAttributes(toAttrs(span.tags(), this.resourceAttr))
				.setTimeEvents(toTimeEvents(span.logs()));
		span.context().toParentSpanId().ifPresent(spanBuilder::setParentSpanId);

		if (span.startUs() > 0) {
			spanBuilder.setStartTime(toTimestamp(span.startUs()));
		}
		if (span.finishUs() > 0) {
			spanBuilder.setEndTime(toTimestamp(span.finishUs()));
		}

		return spanBuilder.build();
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
	private static Timestamp toTimestamp(long microseconds) {
		var remainder = microseconds % 1_000_000;
		return TIMESTAMP_BUILDER.get()
				.setSeconds(MICROSECONDS.toSeconds(microseconds))
				.setNanos((int) MICROSECONDS.toNanos(remainder))
				.build();
	}

	/**
	 *
	 * @param tags
	 * @param resourceAttr
	 * @return
	 */
	private static Attributes toAttrs(@Nonnull Map<String, Object> tags, @Nonnull Map<String, AttributeValue> resourceAttr) {
		var attributesBuilder = toAttrsBuilder(tags);
		attributesBuilder.putAttributeMap(AGENT_LABEL_KEY, AGENT_LABEL_VALUE);
		attributesBuilder.putAllAttributeMap(resourceAttr);
		return attributesBuilder.build();
	}

	/**
	 *
	 * @param logs
	 * @return
	 */
	private static Span.TimeEvents toTimeEvents(@Nonnull List<Log> logs) {
		var timeEventsBuilder = TIME_EVENTS_BUILDER.get();
		logs.forEach(l -> timeEventsBuilder.addTimeEvent(toTimeMessageEvent(l)));
		return timeEventsBuilder.build();
	}

	/**
	 *
	 * @param tags
	 * @return
	 */
	private static Attributes.Builder toAttrsBuilder(@Nonnull Map<String, ?> tags) {
		var attributesBuilder = ATTRS_BUILDER.get();
		tags.forEach((k, v) -> {
			final String key = mapKey(k);
			final AttributeValue value = toAttrValue(key, v);
			if (value != null) {
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
	@javax.annotation.Nullable
	private static AttributeValue toAttrValue(@Nonnull String key, @Nonnull Object value) {
		var builder = ATTR_VALUE_BUILDER.get();
		if (value instanceof CharSequence) {
			builder.setStringValue(toTruncatableString((CharSequence) value));
		} else if (value instanceof Boolean) {
			builder.setBoolValue((Boolean) value);
		} else if (value instanceof Short || value instanceof Integer || value instanceof Long) {
			// FIXME Cloud Trace doesn't like status code as an integer!
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
	 * @param log
	 * @return
	 */
	private static TimeEvent toTimeMessageEvent(Log log) {
		var timeEventBuilder = TIME_EVENT_BUILDER.get().setTime(toTimestamp(log.timeUs));
		var annotationBuilder =
				TIME_EVENT_ANNO_BUILDER.get().setAttributes(toAttrsBuilder(log.fields));
		log.event.ifPresent(e -> annotationBuilder.setDescription(toTruncatableString(e)));
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
		var map = new HashMap<String, AttributeValue>();
		resource.getLabels().forEach((k, v) -> map.put(k, toAttrValue(k, v)));
		return map;
	}

	private static String agent() {
		var pkg = Factory.class.getPackage();

		if (pkg.getImplementationVersion() == null) {
			return "cloud-operations-util [development]";
		}
		return format(
			"%s:%s [%s]",
			pkg.getImplementationVendor(),
			pkg.getImplementationTitle(),
			pkg.getImplementationVersion());
	}
}
