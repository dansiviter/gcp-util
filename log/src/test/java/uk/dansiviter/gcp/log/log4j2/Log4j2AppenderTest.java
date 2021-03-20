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
package uk.dansiviter.gcp.log.log4j2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Log4j2AppenderTest {
	private final MonitoredResource monitoredResource = MonitoredResource.of("global", Map.of());

	@Mock
	private LoggingOptions loggingOptions;
	@Mock
	private Logging logging;

	private Log4j2Appender appender;

	@BeforeEach
	void before() {
		this.appender = Log4j2Appender.newBuilder()
			.setName("test")
			.setLoggingOptions(this.loggingOptions)
			.setMonitoredResource(this.monitoredResource)
			.build();
		when(this.loggingOptions.getService()).thenReturn(this.logging);
		this.appender.start();
	}

	@Test @Disabled("no way of preventing connection to GCP yet!")
	void config() throws IOException {
		// System.setProperty("log4j2.debug", "true");
		var ctx = new LoggerContext("test");
		var url = getClass().getResource("Log4j2AppenderTest.xml");
		var source = new ConfigurationSource(url.openStream(), url);
		var configuration = ConfigurationFactory.getInstance().getConfiguration(ctx, source);
		configuration.start();
		var appender = (Log4j2Appender) configuration.getAppender("cloud");
		assertEquals(1, appender.getDecorators().size());
	}

		@Test
	void append(@Mock LogEvent event) {
		when(event.getLevel()).thenReturn(Level.INFO);
		when(event.getMessage()).thenReturn(new SimpleMessage("foo"));

		appender.append(event);

		verify(this.logging).write(argThat(c -> ((List<LogEntry>) c).size() == 1), any());
	}
}
