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
package uk.dansiviter.gcp.log;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static uk.dansiviter.gcp.Util.threadLocal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.json.Json;
import javax.json.stream.JsonGenerator;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingEnhancer;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Severity;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public enum Factory { ;
	private static final ThreadLocal<LogEntry.Builder> BUILDER = threadLocal(() -> LogEntry.newBuilder(null), b -> {
		b.setInsertId(null);
		b.setHttpRequest(null);
		b.setLogName(null);
		b.clearLabels();
		return b;
	});
	private static final String TYPE = "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";

	/**
	 * Transforms the input entry into Cloud Logging entry.
	 *
	 * @param entry the log entry.
	 * @param decorators the log entry decorators.
	 * @return the Cloud Logging entry.
	 */
	@Nonnull
	public static LogEntry logEntry(@Nonnull Entry entry, @Nonnull List<EntryDecorator> decorators) {
		var b = BUILDER.get()
				.setTimestamp(entry.timestamp())
				.setSeverity(entry.severity());
		entry.logName().ifPresent(t -> b.addLabel("logName", t.toString()));
		entry.threadName().ifPresent(t -> b.addLabel("thread", t.toString()));

		var payload = payload(entry);
		decorators.forEach(d -> d.decorate(b, entry, payload));
		b.setPayload(JsonPayload.of(payload));
		return b.build();
	}

	/**
	 * Converts comma separated list of {@link EntryDecorator} or {@link LoggingEnhancer} class names into instances.
	 *
	 * @param decorators the decorators represented as a comma separated string.
	 * @return a list of decorator instances.
	 */
	@Nonnull
	public static List<EntryDecorator> decorators(@Nonnull String decorators) {
		if (decorators.isBlank()) {
			return emptyList();
		}
		return stream(decorators.split(",")).map(Factory::decorator).collect(toList());
	}

	/**
	 * Creates a {@link EntryDecorator} from the class name. If this is a {@link LoggingEnhancer} then it will be
	 * wrapped.
	 *
	 * @param name the class name.
	 * @return a decorator instance.
	 */
	@Nonnull
	public static EntryDecorator decorator(String name) {
		var instance = instance(name);
		if (instance instanceof EntryDecorator) {
			return (EntryDecorator) instance;
		}
		if (instance instanceof LoggingEnhancer) {
			return EntryDecorator.decorator((LoggingEnhancer) instance);
		}
		throw new IllegalStateException(format("Unsupported type! [%s]", instance));
	}

	/**
	 * Converts the input entry into a {@link Map}.
	 *
	 * @param entry the log entry.
	 * @return the map instance.
	 */
	private static @Nonnull Map<String, Object> payload(@Nonnull Entry entry) {
		var data = new HashMap<String, Object>();

		// doesn't support CharSequence or even the protobuf ByteString
		entry.message().ifPresent(m -> data.put("message", m instanceof String ? m : m.toString()));

		var context = new HashMap<String, Object>();
		if (entry.severity().ordinal() >= Severity.ERROR.ordinal()) {
			data.put("@type", TYPE);  // an Error may not have a stacktrace, force it in regardless
		}
		if (entry.severity().ordinal() >= Severity.WARNING.ordinal() && !entry.thrown().isPresent()) {
			entry.source().ifPresent(s -> context.put("reportLocation", s.asMap()));
		}
		entry.thrown().ifPresent(t -> data.put("stack_trace", t.get().toString()));

		if (!context.isEmpty()) {
			data.put("context", context);
		}

		return data;
	}

	/**
	 * Converts the {@link Throwable} to a {@link CharSequence}.
	 *
	 * @param t the throwable.
	 * @return the character sequence.
	 */
	public static @Nonnull CharSequence toCharSequence(@Nonnull Throwable t) {
		try (var sw = new StringWriter(); var pw = new UnixPrintWriter(sw)) {
			t.printStackTrace(pw);
			return sw.getBuffer();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Create an instance of the given class name using no-args constructor.
	 *
	 * @param <T> the class type.
	 * @param name the class name.
	 * @return a instance of the class.
	 * @throws IllegalArgumentException if the class cannot be created.
	 */
	@SuppressWarnings("unchecked")
	public static @Nonnull <T> T instance(@Nonnull String name) {
		try {
			var concreteCls = Class.forName(name);
			return (T) concreteCls.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException(format("Unable to create! [%s]", name), e);
		}
	}

	/**
	 * Streams the entry to the given {@link OutputStream}.
	 *
	 * @param entry the entry to write.
	 * @param os the target output stream.
	 * @throws IOException thrown if unable to stream.
	 */
	public static void toJson(@Nonnull LogEntry entry, @Nonnull OutputStream os) throws IOException {
		var generator = Json.createGenerator(os);
		generator.writeStartObject()
			.write("severity", entry.getSeverity().toString())
			.write("timestamp", Instant.ofEpochMilli(entry.getTimestamp()).toString())
			.writeKey("jsonPayload");
		JsonPayload jsonPayload = entry.getPayload();
		toJson(generator, jsonPayload.getDataAsMap());

		if (!entry.getLabels().isEmpty()) {
			generator.writeKey("logging.googleapis.com/labels")
				.writeStartObject();
			entry.getLabels().forEach((k, v) -> generator.write(k, v));
			generator.writeEnd();
		}

		var operation = entry.getOperation();
		if (operation != null) {
			generator.writeKey("logging.googleapis.com/operation")
				.writeStartObject();
			if (operation.getId() != null) {
				generator.write("id", operation.getId());
			}
			if (operation.getProducer() != null) {
				generator.write("producer", operation.getProducer());
			}
			if (operation.first()) {
				generator.write("first", operation.first());
			}
			if (operation.last()) {
				generator.write("last", operation.last());
			}
			generator.writeEnd();
		}

		var sourceLocation = entry.getSourceLocation();
		if (sourceLocation != null) {
			generator.writeKey("logging.googleapis.com/sourceLocation")
				.writeStartObject();
			if (sourceLocation.getFile() != null) {
				generator.write("file", sourceLocation.getFile());
			}
			if (sourceLocation.getFunction() != null) {
				generator.write("function", sourceLocation.getFunction());
			}
			if (sourceLocation.getLine() != null) {
				generator.write("line", sourceLocation.getLine());
			}
			generator.writeEnd();
		}

		var traceId = entry.getTrace();
		if (traceId != null) {
			generator.write("logging.googleapis.com/trace", traceId);
		}
		var spanId = entry.getSpanId();
		if (spanId != null) {
			generator.write("logging.googleapis.com/spanId", spanId);
		}

		if (entry.getTraceSampled()) {
			generator.write("logging.googleapis.com/trace_sampled", true);
		}
		generator.writeEnd().close();
	}

	@SuppressWarnings("unchecked")
	private static void toJson(JsonGenerator generator, Map<String, Object> map) {
		generator.writeStartObject();
		map.forEach((k, v) -> {
			if (v instanceof Map) {
				generator.writeKey(k);
				toJson(generator, (Map<String, Object>) v);
			} else if (v instanceof String) {
				generator.write(k, (String) v);
			} else {
				throw new IllegalArgumentException("Unexpected type! [" + v + "]");
			}
		});
		generator.writeEnd();
	}

	/**
	 * A PrintWriter that forces Unix EoL.
	 */
	private static class UnixPrintWriter extends PrintWriter {
		UnixPrintWriter(Writer writer) {
			super(writer);
		}

		@Override
		public void println() {
			write('\n');
		}
	}
}
