package uk.dansiviter.gcp.log;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Payload.StringPayload;

/**
 * JSON factory.
 */
public enum JsonFactory { ;

	/**
	 * Streams the entry to the given {@link OutputStream}.
	 *
	 * @param entry the entry to write.
	 * @param os the target output stream.
	 */
	public static void toJson(LogEntry entry, OutputStream os) {
		try (var generator = Json.createGenerator(os)) {
			generator.writeStartObject()
				.write("severity", entry.getSeverity().toString())
				.write("time", entry.getInstantTimestamp().toString());
			var payload = entry.getPayload();

			if (payload instanceof JsonPayload) {
				addMap(generator, ((JsonPayload) payload).getDataAsMap());
			} else {
				generator.write("message", ((StringPayload) payload).getData());
			}

			if (!entry.getLabels().isEmpty()) {
				generator.writeKey("logging.googleapis.com/labels")
					.writeStartObject();
				entry.getLabels().entrySet().stream()
					.forEach(e -> generator.write(e.getKey(), e.getValue()));
				generator.writeEnd();
			}

			var insertId = entry.getInsertId();
			if (insertId != null) {
				generator.write("logging.googleapis.com/insertId", insertId);
			}

			addOperation(entry, generator);
			addSourceLocation(entry, generator);
			addTrace(entry, generator);

			generator.writeEnd();
		}
	}

	private static void addSourceLocation(LogEntry entry, JsonGenerator generator) {
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
	}

	private static void addTrace(LogEntry entry, JsonGenerator generator) {
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
	}
}
