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
package uk.dansiviter.gcp.opentelemetry.trace;

import static java.util.stream.Collectors.toList;

import static com.google.devtools.cloudtrace.v2.Span.Link.Type.CHILD_LINKED_SPAN;
import static com.google.devtools.cloudtrace.v2.Span.Link.Type.PARENT_LINKED_SPAN;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_HOST;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_USER_AGENT;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static uk.dansiviter.gcp.AtomicInit.atomic;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.devtools.cloudtrace.v2.AttributeValue;
import com.google.devtools.cloudtrace.v2.ProjectName;
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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import uk.dansiviter.gcp.AtomicInit;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Feb 2020]
 */
public class Factory {
	private final AtomicInit<SpanName.Builder> spanNameBuilder =
		atomic(SpanName::newBuilder);
	private final AtomicInit<Span.Builder> spanBuilder =
			atomic(Span::newBuilder, Span.Builder::clear);
	private final AtomicInit<TruncatableString.Builder> stringBuilder =
			atomic(TruncatableString::newBuilder, TruncatableString.Builder::clear);
	private final AtomicInit<Timestamp.Builder> timestampBuilder =
			atomic(Timestamp::newBuilder, Timestamp.Builder::clear);
	private final AtomicInit<Attributes.Builder> attrsBuilder =
			atomic(Attributes::newBuilder, Attributes.Builder::clear);
	private final AtomicInit<AttributeValue.Builder> attrsValueBuilder =
			atomic(AttributeValue::newBuilder, AttributeValue.Builder::clear);
	private final AtomicInit<TimeEvents.Builder> timeEventsBuilder =
			atomic(TimeEvents::newBuilder, TimeEvents.Builder::clear);
	private final AtomicInit<TimeEvent.Builder> timeEventBuilder =
			atomic(TimeEvent::newBuilder, TimeEvent.Builder::clear);
	private final AtomicInit<TimeEvent.Annotation.Builder> timeEventAnnoBuilder =
			atomic(TimeEvent.Annotation::newBuilder, TimeEvent.Annotation.Builder::clear);

	private static final String AGENT_LABEL_KEY = "/agent";
	private static final Map<String, String> HTTP_ATTRIBUTE_MAPPING = Map.of(
		HTTP_HOST.getKey(), "/http/host",
		HTTP_METHOD.getKey(), "/http/method",
		"http.path", "/http/path",  // no SemanticAttributes alternative
		HTTP_URL.getKey(), "/http/url",
		HTTP_ROUTE.getKey(), "/http/route",
		HTTP_USER_AGENT.getKey(), "/http/user_agent",
		HTTP_STATUS_CODE.getKey(), "/http/status_code");

	private final AttributeValue agentLabelValue =
			AttributeValue.newBuilder().setStringValue(toTruncatableString(agent())).build();

	private final ProjectName projectName;

	private final Map<String, AttributeValue> resourceAttr;

	Factory(MonitoredResource resource, ProjectName projectName) {
		this.resourceAttr = toAttrs(resource);
		this.projectName = projectName;
	}

	/**
	 *
	 * @param spans
	 * @return
	 */
	List<Span> toSpans(Collection<SpanData> spans) {
		return spans.stream().map(this::toSpan).collect(toList());
	}

	private Span toSpan(SpanData span) {
		var ctx = span.getSpanContext();
		var spanId = ctx.getSpanId();
		var spanName = spanNameBuilder.get() // no clear method, but should override all fields anyway
				.setProject(this.projectName.getProject())
				.setTrace(ctx.getTraceId()).setSpan(spanId).build();

		var builder = spanBuilder.get().setName(spanName.toString()).setSpanId(spanId)
				.setDisplayName(toTruncatableString(span.getName()))
				.setAttributes(toAttrs(span.getAttributes(), this.resourceAttr))
				.setTimeEvents(toTimeEvents(span.getEvents()))
				.setLinks(toLinks(span.getLinks()));

		var parentSpanId = ctx.getTraceState().get("parentSpanId");
		if (parentSpanId != null) {
			builder.setParentSpanId(parentSpanId);
		}

		status(span.getStatus()).ifPresent(builder::setStatus);

		if (span.getStartEpochNanos() == 0L) {
			throw new IllegalStateException("Incomplete span! No start time.");
		}
		if (span.getEndEpochNanos() == 0L) {
			throw new IllegalStateException("Incomplete span! No end time.");
		}

		builder.setStartTime(toTimestamp(span.getStartEpochNanos()));
		builder.setEndTime(toTimestamp(span.getEndEpochNanos()));

		return builder.build();
	}

	private static Optional<Status> status(StatusData data) {
		var statusBuilder = Status.newBuilder();
		statusBuilder.setCode(data.getStatusCode().ordinal());
		if (data.getDescription() != null && !data.getDescription().isEmpty()) {
			statusBuilder.setMessage(data.getDescription());
		}
		return Optional.of(statusBuilder.build());
	}

	/**
	 *
	 * @param charSeq
	 * @return
	 */
	private TruncatableString toTruncatableString(@Nonnull CharSequence charSeq) {
		return stringBuilder.get().setValue(charSeq.toString()).build();
	}

	/**
	 *
	 * @param microseconds
	 * @return
	 */
	Timestamp toTimestamp(long epochNanos) {
		long seconds = NANOSECONDS.toSeconds(epochNanos);
		return timestampBuilder.get()
				.setSeconds(seconds)
				.setNanos((int) (epochNanos - SECONDS.toNanos(seconds)))
				.build();
	}

	/**
	 *
	 * @param tags
	 * @param resourceAttr
	 * @return
	 */
	private Attributes toAttrs(
			@Nonnull io.opentelemetry.api.common.Attributes attrs,
			@Nonnull Map<String, AttributeValue> resourceAttr)
	{
		var attributesBuilder = toAttrsBuilder(attrs);
		attributesBuilder.putAttributeMap(AGENT_LABEL_KEY, agentLabelValue);
		attributesBuilder.putAllAttributeMap(resourceAttr);
		return attributesBuilder.build();
	}

	/**
	 *
	 * @param logs
	 * @return
	 */
	private Span.TimeEvents toTimeEvents(@Nonnull List<EventData> events) {
		var builder = timeEventsBuilder.get();
		events.forEach(e -> builder.addTimeEvent(toTimeMessageEvent(e)));
		return builder.build();
	}

	/**
	 *
	 * @param tags
	 * @return
	 */
	private Attributes.Builder toAttrsBuilder(
			@Nonnull io.opentelemetry.api.common.Attributes attrs)
	{
		final Attributes.Builder attributesBuilder = attrsBuilder.get();
		attrs.forEach((k, v) -> {
				var key = HTTP_ATTRIBUTE_MAPPING.getOrDefault(k.getKey(), k.getKey());
				var value = toAttrValue(k, v);
				if (value != null) {
					attributesBuilder.putAttributeMap(key, value);
				}
					attributesBuilder.putAttributeMap(key, value);
				});
		return attributesBuilder;
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	private AttributeValue toAttrValue(@Nonnull String key, @Nonnull Object value) {
		AttributeType type = null;
		if (value instanceof CharSequence) {
			type = AttributeType.STRING;
		} else if (value instanceof Boolean) {
			type = AttributeType.BOOLEAN;
		} else if (value instanceof Short || value instanceof Integer || value instanceof Long) {
			type = AttributeType.LONG;
		}
		return toAttrValue(key, type, value);
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	private AttributeValue toAttrValue(@Nonnull AttributeKey<?> key, @Nonnull Object value) {
		return toAttrValue(key.getKey(), key.getType(), value);
	}

	/**
	 *
	 * @param value
	 * @return
	 */
	private AttributeValue toAttrValue(@Nonnull String key, AttributeType type, @Nonnull Object value) {
		var builder = attrsValueBuilder.get();
		if (type == null) {
			builder.setStringValue(toTruncatableString(value.toString()));
			return builder.build();
		}
		switch (type) {
		case BOOLEAN:
			builder.setBoolValue((Boolean) value);
			break;
		case DOUBLE:
			builder.setStringValue(toTruncatableString(Double.toString((Double) value)));
			break;
		case LONG:
			// FIXME Cloud Trace doesn't like status code as an integer!
			// https://issuetracker.google.com/149088139
			if ("/http/status_code".equals(key)) {
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

	private Link toLink(LinkData link) {
		return Link.newBuilder().setTraceId(link.getSpanContext().getTraceId())
				.setSpanId(link.getSpanContext().getSpanId())
				.setType(link.getSpanContext().isRemote() ? PARENT_LINKED_SPAN : CHILD_LINKED_SPAN)
				.setAttributes(toAttrsBuilder(link.getAttributes())).build();
	}

	private Links toLinks(List<LinkData> links) {
		var linksBuilder = Links.newBuilder();
		links.forEach(l -> linksBuilder.addLink(toLink(l)));
		return linksBuilder.build();
	}

	/**
	 *
	 * @param log
	 * @return
	 */
	private TimeEvent toTimeMessageEvent(EventData event) {
		var builder = timeEventBuilder.get()
				.setTime(toTimestamp(event.getEpochNanos()))
				.setAnnotation(timeEventAnnoBuilder.get()
						.setAttributes(toAttrsBuilder(event.getAttributes()))
						.setDescription(toTruncatableString(event.getName())));
		return builder.build();
	}

	/**
	 *
	 * @param resource
	 * @return
	 */
	private Map<String, AttributeValue> toAttrs(MonitoredResource resource) {
		var map = new HashMap<String, AttributeValue>();
		resource.getLabels().forEach((k, v) -> map.put(k, toAttrValue(k, v)));
		return map;
	}

	private String agent() {
		var pkg = Factory.class.getPackage();
		return format(
			"%s [%s]",
			pkg.getName(),
			pkg.getImplementationVersion() != null ? pkg.getImplementationVersion() : "develop.");
	}
}
