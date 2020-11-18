package uk.dansiviter.gcp.monitoring.log.jul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BasicFormatter}.
 */
@ExtendWith(MockitoExtension.class)
public class BasicFormatterTest {
	@InjectMocks
	private BasicFormatter formatter;

	@Test
	public void format(@Mock LogRecord record) {
		Object[] params = { "acme", (Supplier<?>) () -> "foo" };
		when(record.getParameters()).thenReturn(params);
		when(record.getMessage()).thenReturn("Hello {0} [{1}]");

		String actual = formatter.format(record);

		assertEquals("Hello acme [foo]", actual);
	}
}
