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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.WriteOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.status.ErrorStatus;
import uk.dansiviter.gcp.ResourceType;
import uk.dansiviter.gcp.log.Entry;
import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.Factory;

/**
 * A Logback implementation of {@link Appender}.
 *
 * <pre>
 * &lt;configuration&gt;
 *   &lt;appender name="GCP" class="uk.dansiviter.gcp.log.logback.LogbackAppender"&gt;
 *     &lt;logName&gt;java.log&lt;/logName&gt;
 *     &lt;synchronicity&gt;ASYNC&lt;/synchronicity&gt;
 *     &lt;decorators&gt;uk.dansiviter.gcp.log.opentelemetry.Decorator&lt;/decorators&gt;
 *   &lt;/appender&gt;
*    &lt;root level="DEBUG"&gt;
 *     &lt;appender-ref ref="GCP" /&gt;
 *   &lt;/root&gt;
 * &lt;/configuration&gt;
 * </pre>
 *
 * @author Daniel Siviter
 * @since v1.0 [29 Jan 2021]
 */
public class LogbackAppender extends AppenderBase<ILoggingEvent> {
	private final List<EntryDecorator> decorators = new LinkedList<>();
	private final ThrowableProxyConverter throwableConverter = new ThrowableProxyConverter();
	private final MonitoredResource monitoredResource;
	private String logName;
	private Synchronicity synchronicity;
	private Severity flushSeverity;
	private LoggingOptions loggingOptions;
	private WriteOption[] defaultWriteOptions;

	private Logging logging;

	/**
	 * A new appender instance with default {@link LoggingOptions} and auto-detected {@link MonitoredResource}.
	 */
	public LogbackAppender() {
		this(LoggingOptions.getDefaultInstance(), ResourceType.monitoredResource());
	}

	/**
	 * A new appender instance.
	 *
	 * @param loggingOptions the logging options.
	 * @param monitoredResource the monitored resource.
	 */
	LogbackAppender(
		@Nonnull LoggingOptions loggingOptions,
		@Nonnull MonitoredResource monitoredResource)
	{
		this.monitoredResource = requireNonNull(monitoredResource);
		try {
			this.loggingOptions = requireNonNull(loggingOptions);
		} catch (RuntimeException e) {
			addStatus(new ErrorStatus("Unable to <init> appender named \"" + name + "\".", this, e));
			throw e;
		}
	}

	/**
	 * @return the log name.
	 */
	public String getLogName() {
		return logName;
	}

	/**
	 * @param logName the log name to set.
	 */
	public void setLogName(String logName) {
		this.logName = logName;
	}

	/**
	 * @return the write synchronicity.
	 */
	public Synchronicity getSynchronicity() {
		return synchronicity;
	}

	/**
	 * @param synchronicity the write synchronicity to set.
	 */
	public void setSynchronicity(Synchronicity synchronicity) {
		this.synchronicity = synchronicity;
		if (isStarted()) {
			this.logging.setWriteSynchronicity(this.synchronicity);
		}
	}

	/**
	 * @return the flush severity.
	 */
	public Severity getFlushSeverity() {
		return flushSeverity;
	}

	/**
	 * @param flushSeverity the flush severity to set.
	 */
	public void setFlushSeverity(Severity flushSeverity) {
		this.flushSeverity = flushSeverity;
		if (isStarted()) {
			this.logging.setFlushSeverity(flushSeverity);
		}
	}

	/**
	 * @param decorators the decorators to set.
	 */
	public void setDecorators(String decorators) {
		this.decorators.addAll(Factory.decorators(decorators));
	}

	/**
	 * @return the decorators.
	 */
	public String getDecorators() {
		return decorators.stream().map(d -> d.getClass().getName()).collect(joining(","));
	}

	@Override
	public void start() {
		this.defaultWriteOptions = new WriteOption[] {
				WriteOption.logName(logName != null ? logName : "java.log"),
				WriteOption.resource(monitoredResource)
		};

		try {
			this.logging = this.loggingOptions.getService();
			if (this.synchronicity != null) {
				this.logging.setWriteSynchronicity(this.synchronicity);
			}
			if (this.flushSeverity != null) {
				this.logging.setFlushSeverity(this.flushSeverity);
			}
			super.start();
		} catch (RuntimeException e) {
			addStatus(new ErrorStatus("Unable to start appender named \"" + name + "\".", this, e));
		}
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		LogEntry entry;
		try {
			entry = uk.dansiviter.gcp.log.Factory.logEntry(new LogbackEntry(eventObject), this.decorators);
		} catch (RuntimeException e) {
			addStatus(new ErrorStatus("Unable to stop appender named \"" + name + "\".", this, e));
			return;
		}
		try {
			this.logging.write(List.of(entry), this.defaultWriteOptions);
		} catch (RuntimeException e) {
			addStatus(new ErrorStatus("Unable to stop appender named \"" + name + "\".", this, e));
		}
	}

	@Override
	public void stop() {
		if (this.logging != null) {
			try {
				this.logging.close();
				super.stop();
			} catch (Exception e) {
				addStatus(new ErrorStatus("Unable to stop appender named \"" + name + "\".", this, e));
			}
		}
	}


	// --- Static Methods ---

	/**
	 *
	 * @param level
	 * @return
	 */
	private static Severity severity(Level level) {
		if (Level.ERROR.equals(level)) {
			return Severity.ERROR;
		} else if (Level.WARN.equals(level)) {
			return Severity.WARNING;
		} else if (Level.INFO.equals(level)) {
			return Severity.INFO;
		} else if (Level.DEBUG.equals(level) || Level.TRACE.equals(level)) {
			return Severity.DEBUG;
		}
		return Severity.DEFAULT;
	}

	// --- Inner Classes ---

	/**
	 *
	 */
	private class LogbackEntry implements Entry {
		private final ILoggingEvent delegate;

		LogbackEntry(ILoggingEvent delegate) {
			this.delegate = delegate;
		}

		@Override
		public Severity severity() {
			return LogbackAppender.severity(this.delegate.getLevel());
		}

		@Override
		public long timestamp() {
			return this.delegate.getTimeStamp();
		}

		@Override
		public Optional<Source> source() {
			var caller = delegate.getCallerData();
			if (caller == null || caller.length == 0) {
				return Optional.empty();
			}
			var first = caller[0];
			return Optional.of(new Source() {
				@Override
				public String className() {
					return first.getClassName();
				}

				@Override
				public String method() {
					return first.getMethodName();
				}

				@Override
				public OptionalInt line() {
					return OptionalInt.of(first.getLineNumber());
				}
			});
		}

		@Override
		public Optional<CharSequence> message() {
			var msg = this.delegate.getFormattedMessage();
			return msg == null || msg.isEmpty() ? Optional.empty() : Optional.of(msg);
		}

		@Override
		public Optional<Supplier<? super CharSequence>> thrown() {
			final var t = this.delegate.getThrowableProxy();
			if (t == null) {
				return Optional.empty();
			}
			return Optional.of(() -> throwableConverter.convert(this.delegate));
		}

		@Override
		public Optional<CharSequence> logName() {
			return Optional.ofNullable(this.delegate.getLoggerName());
		}

		@Override
		public Optional<CharSequence> threadName() {
			return Optional.ofNullable(this.delegate.getThreadName());
		}
	}
}
