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
package uk.dansiviter.gcp.log.logback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

@ExtendWith(MockitoExtension.class)
public class LogbackAppenderTest {
	private final MonitoredResource monitoredResource = MonitoredResource.of("global", Map.of());

	@Mock
	private LoggingOptions loggingOptions;
	@Mock
	private Logging logging;

	private LogbackAppender appender;

	@BeforeEach
	public void before() {
		this.appender = new LogbackAppender(this.loggingOptions, this.monitoredResource);
		when(this.loggingOptions.getService()).thenReturn(this.logging);

		this.appender.start();
	}

	@Test
	public void doAppend(@Mock ILoggingEvent event) {
		when(event.getLevel()).thenReturn(Level.INFO);

		appender.doAppend(event);

		verify(this.logging).write(argThat(c -> ((List<LogEntry>) c).size() == 1), any());
	}
}
