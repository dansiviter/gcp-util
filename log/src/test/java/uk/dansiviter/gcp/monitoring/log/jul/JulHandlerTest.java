package uk.dansiviter.gcp.monitoring.log.jul;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link JulHandler}.
 */
@ExtendWith(MockitoExtension.class)
public class JulHandlerTest {
	private final MonitoredResource monitoredResource = MonitoredResource.of("global", Map.of());

	@Mock
	private LoggingOptions loggingOptions;
	@Mock
	private Logging logging;


	private JulHandler handler;

	@BeforeEach
	public void before() {
		this.handler = JulHandler.julHandler(this.loggingOptions, this.monitoredResource);
		when(this.loggingOptions.getService()).thenReturn(this.logging);
	}

	@Test
	public void publish(@Mock LogRecord record) {
		when(record.getLevel()).thenReturn(Level.INFO);

		handler.publish(record);

		verify(this.logging).write(argThat(c -> ((List<LogEntry>) c).size() == 1), any());
	}
}
