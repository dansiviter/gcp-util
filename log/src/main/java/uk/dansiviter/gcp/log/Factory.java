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
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.dansiviter.gcp.Util.threadLocal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonValue;
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
	private static final String NANO_TIME = "nanoTime";
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
	public static LogEntry logEntry(Entry entry, List<EntryDecorator> decorators) {
		var timestamp = entry.timestamp();
		var b = BUILDER.get()
				.setTimestamp(timestamp.toEpochMilli())
				.setSeverity(entry.severity());
		if (timestamp.getNano() > 0) {  // googleapis/java-logging#598
			b.addLabel(NANO_TIME, timestamp.toString());
		}

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
	public static List<EntryDecorator> decorators(String decorators) {
		requireNonNull(decorators, "'decorators' must not be null!");
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
	private static Map<String, Object> payload(Entry entry) {
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
	 * @throws IllegalStateException if unable to write.
	 */
	public static CharSequence toCharSequence(Throwable t) {
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
	public static <T> T instance(String name) {
		requireNonNull(name, "'name' must not be null!");
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
	 */
	public static void toJson(LogEntry entry, OutputStream os) {
		var generator = Json.createGenerator(os);
		var precisionTime = entry.getLabels().get(NANO_TIME);
		generator.writeStartObject()
			.write("severity", entry.getSeverity().toString())
			.write("time", precisionTime != null ? precisionTime : Instant.ofEpochMilli(entry.getTimestamp()).toString());
		JsonPayload jsonPayload = entry.getPayload();
		toJson(generator, jsonPayload.getDataAsMap());

		if (!entry.getLabels().isEmpty()) {
			generator.writeKey("logging.googleapis.com/labels")
				.writeStartObject();
			entry.getLabels().entrySet().stream()
				.filter(e -> !NANO_TIME.equals(e.getKey()))
				.forEach(e -> generator.write(e.getKey(), e.getValue()));
			generator.writeEnd();
		}

		var insertId = entry.getInsertId();
		if (insertId != null) {
			generator.write("logging.googleapis.com/insertId", insertId);
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
		map.forEach((k, v) -> {
			if (v instanceof String) {
				generator.write(k, (String) v);
			} else if (v instanceof Boolean) {
				generator.write(k, (Boolean) v);
			} else if (v instanceof Integer || v instanceof Short) {
				generator.write(k, ((Number) v).intValue());
			} else if (v instanceof Long) {
				generator.write(k, (Long) v);
			} else if (v instanceof BigInteger) {
				generator.write(k, (BigInteger) v);
			} else if (v instanceof Float || v instanceof Double) {
				generator.write(k, ((Number) v).doubleValue());
			} else if (v instanceof BigDecimal) {
				generator.write(k, (BigDecimal) v);
			} else if (v instanceof Map) {
				generator.writeKey(k).writeStartObject();;
				toJson(generator, (Map<String, Object>) v);
				generator.writeEnd();
			} else if (v instanceof JsonValue) {
				generator.write(k, (JsonValue) v);
			} else {
				throw new IllegalArgumentException("Unexpected type! [" + v + "]");
			}
		});
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
