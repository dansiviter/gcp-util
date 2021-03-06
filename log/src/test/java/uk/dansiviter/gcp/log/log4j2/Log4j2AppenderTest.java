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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;

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

/**
 * Tests for {@link Log4j2Appender}.
 */
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
		var ctx = new LoggerContext("test");
		var url = getClass().getResource("Log4j2AppenderTest.xml");
		var source = new ConfigurationSource(url.openStream(), url);
		var configuration = ConfigurationFactory.getInstance().getConfiguration(ctx, source);
		configuration.start();
		var appender = (Log4j2Appender) configuration.getAppender("cloud");
		assertThat(appender.getDecorators(), hasSize(1));
	}

		@Test
	void append(@Mock LogEvent event) {
		when(event.getLevel()).thenReturn(Level.INFO);
		when(event.getMessage()).thenReturn(new SimpleMessage("foo"));

		appender.append(event);

		verify(this.logging).write(argThat(c -> ((List<LogEntry>) c).size() == 1), any());
	}

	@Test
	void stop() throws Exception {
		appender.stop(1, TimeUnit.SECONDS);

		verify(this.logging).close();
	}

	@Test
	void toString_() throws Exception {
		assertThat(appender.toString(), is("test{name=test}"));
	}

	@Test
	void severity() {
		assertThat(Log4j2Appender.severity(Level.ERROR), is(Severity.ERROR));
		assertThat(Log4j2Appender.severity(Level.WARN), is(Severity.WARNING));
		assertThat(Log4j2Appender.severity(Level.INFO), is(Severity.INFO));
		assertThat(Log4j2Appender.severity(Level.TRACE), is(Severity.DEBUG));
		assertThat(Log4j2Appender.severity(Level.DEBUG), is(Severity.DEBUG));
	}
}
