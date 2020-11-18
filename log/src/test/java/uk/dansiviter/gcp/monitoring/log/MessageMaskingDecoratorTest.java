package uk.dansiviter.gcp.monitoring.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import com.google.cloud.logging.LogEntry.Builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link MessageMaskingDecorator}
 */
@ExtendWith(MockitoExtension.class)
public class MessageMaskingDecoratorTest {
	@Test
	public void decorate(@Mock Builder b, @Mock Entry e) {
		var decorator = new MessageMaskingDecorator("foo");

		var payload = new HashMap<String, Object>();
		payload.put("message", "hello");
		decorator.decorate(b, e, payload);
		assertEquals("hello", payload.get("message"));

		payload.put("message", "hello foo");
		decorator.decorate(b, e, payload);
		assertEquals("hello **REDACTED**", payload.get("message"));

		payload.put("message", "foo hello");
		decorator.decorate(b, e, payload);
		assertEquals("**REDACTED** hello", payload.get("message"));
	}
}
