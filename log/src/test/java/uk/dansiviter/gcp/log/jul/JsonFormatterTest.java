package uk.dansiviter.gcp.log.jul;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link JsonFormatter}.
 */
@ExtendWith(MockitoExtension.class)
public class JsonFormatterTest {
	@Test
	public void format(@Mock LogRecord record) {
		var formatter = new JsonFormatter();
		var actual = formatter.format(record);
		assertThat(actual, is("{\"jsonPayload\":{},\"timestamp\":\"1970-01-01T00:00:00Z\"}"));
	}
}
