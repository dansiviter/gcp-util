package uk.dansiviter.gcp.log.jul;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.json.Json;
import javax.json.JsonObject;

import com.google.cloud.logging.Operation;
import com.google.cloud.logging.LogEntry.Builder;

import org.junit.jupiter.api.Test;

import uk.dansiviter.gcp.log.Entry;
import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.ServiceContextDecorator;

/**
 * Tests for {@link JsonFormatter}.
 */
class JsonFormatterTest {
	@Test
	void format() {
		var record = new LogRecord(Level.SEVERE, "Hello world!");
		record.setLoggerName("test");
		record.setInstant(Instant.parse("2021-07-26T13:14:15.016Z"));

		var formatter = new JsonFormatter();
		formatter.setDecorators(KitchenSink.class.getName());
		formatter.addDecorators(new ServiceContextDecorator("testService", "testVersion"));
		var actual = formatter.format(record);

		var expected =
			"{" +
				"\"severity\":\"ERROR\"," +
				"\"timestamp\":\"2021-07-26T13:14:15.016Z\"," +
				"\"jsonPayload\":{" +
					"\"@type\":\"type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent\"," +
					"\"message\":\"Hello world!\"," +
					"\"serviceContext\":{" +
						"\"service\":\"testService\"," +
						"\"version\":\"testVersion\"" +
					"}" +
				"}," +
				"\"logging.googleapis.com/labels\":{" +
					"\"logName\":\"test\"," +
					"\"Hello\":\"world\"," +
					"\"thread\":\"main\"" +
				"}," +
				"\"logging.googleapis.com/operation\":{" +
					"\"id\":\"testId\"," +
					"\"producer\":\"testProducer\"," +
					"\"first\":true," +
					"\"last\":true" +
				"}," +
				"\"logging.googleapis.com/trace\":\"projects/foo/traces/00000000000000000000000000000001\"," +
				"\"logging.googleapis.com/spanId\":\"0000000000000002\"," +
				"\"logging.googleapis.com/trace_sampled\":true" +
			"}";
		assertThat(parse(actual), is(parse(expected)));
	}

	private static JsonObject parse(String json) {
		try (var is = new ByteArrayInputStream(json.getBytes(UTF_8))) {
			return Json.createReader(is).readObject();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static class KitchenSink implements EntryDecorator {
		@Override
		public void decorate(Builder b, Entry e, Map<String, Object> payload) {
			b.setOperation(Operation
				.newBuilder("testId", "testProducer")
				.setFirst(true)
				.setLast(true)
				.build());

			b.addLabel("Hello", "world");
			b.setSpanId("0000000000000002");
			b.setTrace("projects/foo/traces/00000000000000000000000000000001");
			b.setTraceSampled(true);
		}
	}
}
