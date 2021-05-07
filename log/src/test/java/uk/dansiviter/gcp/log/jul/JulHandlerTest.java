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
package uk.dansiviter.gcp.log.jul;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingLevel;
import com.google.cloud.logging.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link JulHandler}.
 */
@ExtendWith(MockitoExtension.class)
class JulHandlerTest {
	private final MonitoredResource monitoredResource = MonitoredResource.of("global", Map.of());

	@Mock
	private Logging logging;

	private JulHandler handler;

	@BeforeEach
	void before() {
		this.handler = new JulHandler(Optional.empty(), () -> this.monitoredResource, () -> this.logging);
	}

	@Test
	void publish(@Mock LogRecord record) {
		when(record.getLevel()).thenReturn(Level.INFO);

		handler.publish(record);
		handler.flush();

		verify(this.logging, timeout(100)).write(argThat(c -> ((Collection<LogEntry>) c).size() == 1), any());
	}

	@Test
	void close(@Mock LogRecord record) {
		handler.close();

		assertThat(handler.isClosed(), is(true));
	}

	@Test
	void severity() {
		assertThat(JulHandler.severity(Level.SEVERE), is(Severity.ERROR));
		assertThat(JulHandler.severity(Level.WARNING), is(Severity.WARNING));
		assertThat(JulHandler.severity(Level.INFO), is(Severity.INFO));
		assertThat(JulHandler.severity(Level.FINE), is(Severity.DEBUG));
		assertThat(JulHandler.severity(Level.FINER), is(Severity.DEBUG));
		assertThat(JulHandler.severity(Level.FINEST), is(Severity.DEBUG));

		assertThat(JulHandler.severity(LoggingLevel.INFO), is(Severity.INFO));
	}
}
