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

import static java.util.stream.Collectors.toList;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.WriteOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;

import uk.dansiviter.gcp.log.Entry;
import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.Factory;

/**
 * A Log4J v2 implementation of {@link Appender}. It's recommended this is used
 * with a {@link org.apache.logging.log4j.core.appender.FailoverAppender} so
 * that problems will be chained rather than lost.
 *
 * <pre>
 * &lt;Configuration status="WARN"&gt;
 *   &lt;Appenders&gt;
 *     &lt;CloudLogging name="java.log" synchronicity="ASYNC"&gt;
 *       &lt;Decorators&gt;
 *         &lt;Decorator class="uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator"/&gt;
 *       &lt;/Decorators&gt;
 *       &lt;Filter .../&gt;
 *     &lt;/CloudLogging&gt;
 *   &lt;/Appenders&gt;
 *   ...
 * &lt;/Configuration&gt;
 * </pre>
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
@Plugin(name = "CloudLogging", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class Log4j2Appender extends AbstractAppender {
	private final List<EntryDecorator> decorators = new LinkedList<>();
	private final Optional<Synchronicity> synchronicity;
	private final Optional<Severity> flushSeverity;
	private final Optional<LoggingOptions> loggingOptions;
	private final WriteOption[] defaultWriteOptions;

	private Logging logging;

	/**
	 * @param builder the builder.
	 */
	protected Log4j2Appender(Builder<?> builder) {
		super(builder.getName(), builder.getFilter(), builder.getOrCreateLayout(), builder.isIgnoreExceptions(),
				builder.getPropertyArray());

		this.loggingOptions = Optional.ofNullable(builder.loggingOptions);
		this.synchronicity = Optional.ofNullable(builder.synchronicity);
		this.flushSeverity = Optional.ofNullable(builder.flushSeverity);
		Optional.ofNullable(builder.decorators).map(Log4j2Appender::decorators).ifPresent(this.decorators::addAll);

		this.defaultWriteOptions = new WriteOption[] { WriteOption.logName(builder.getName()),
				WriteOption.resource(builder.monitoredResource) };
	}

	/**
	 * @return the entry decorators.
	 */
	List<EntryDecorator> getDecorators() {
		return decorators;
	}

	@Override
	public void start() {
		setStarting();

		try {
			this.logging = this.loggingOptions
					.orElseGet(LoggingOptions::getDefaultInstance)
					.getService();
			this.synchronicity.ifPresent(this.logging::setWriteSynchronicity);
			this.flushSeverity.ifPresent(this.logging::setFlushSeverity);
			super.start();
		} catch (RuntimeException e) {
			error("Unable to start!", e);
		}
	}

	@Override
	public void append(LogEvent event) {
		if (!isStarted()) {
			error("Log4j2Appender not started!");
			return;
		}

		LogEntry entry;
		try {
			entry = Factory.logEntry(new Log4J2Entry(event), this.decorators);
		} catch (RuntimeException e) {
			error(e.getLocalizedMessage(), e);
			return;
		}
		try {
			this.logging.write(List.of(entry), this.defaultWriteOptions);
		} catch (RuntimeException e) {
			error(e.getLocalizedMessage(), e);
		}
	}

	@Override
	public boolean stop(final long timeout, final TimeUnit timeUnit) {
		setStopping();
		var stopped = super.stop(timeout, timeUnit, false);

		if (this.logging != null) {
			try {
				this.logging.close();
			} catch (Exception e) {
				error("Unable to close!", e);
			}
		}

		setStopped();
		return stopped;
	}

	@Override
	public String toString() {
		return super.toString() + "{name=" + getName() + '}';
	}


	// --- Static Methods ---

	/**
	 * New builder instance.
	 *
	 * @param <B> builder type.
	 * @return a new instance of the builder.
	 */
	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}

	/**
	 * @param level the severity level.
	 * @return the equivalent severity or {@link Severity#DEFAULT}.
	 */
	static Severity severity(Level level) {
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

	/**
	 * Coverts {@link DecoratorItem} to {@link EntryDecorator}.
	 *
	 * @param decorators the items to convert.
	 * @return the list of decorators.
	 */
	public static List<EntryDecorator> decorators(DecoratorItem... decorators) {
		if (decorators == null || decorators.length == 0) {
			return Collections.emptyList();
		}
		return Arrays.stream(decorators).map(DecoratorItem::getClassName).map(Factory::decorator).collect(toList());
	}


	// --- Inner Classes ---

	/**
	 * A Log4j entry.
	 */
	private class Log4J2Entry implements Entry {
		private final LogEvent delegate;

		/**
		 *
		 * @param delegate
		 */
		Log4J2Entry(LogEvent delegate) {
			this.delegate = delegate;
		}

		@Override
		public Severity severity() {
			return Log4j2Appender.severity(this.delegate.getLevel());
		}

		@Override
		public Instant timestamp() {
			return Instant.ofEpochMilli(this.delegate.getTimeMillis());
		}

		@Override
		public Optional<Source> source() {
			if (delegate.getSource() == null) {
				return Optional.empty();
			}
			return Optional.of(new Source() {
				@Override
				public String className() {
					return delegate.getSource().getClassName();
				}

				@Override
				public String method() {
					return delegate.getSource().getMethodName();
				}

				@Override
				public OptionalInt line() {
					return OptionalInt.of(delegate.getSource().getLineNumber());
				}
			});
		}

		@Override
		public Optional<CharSequence> message() {
			var bytes = getLayout().toByteArray(this.delegate);
			Charset charset;
			if (getLayout() instanceof StringLayout) {
				charset = ((StringLayout) getLayout()).getCharset();
			} else {
				charset = Charset.defaultCharset();
			}
			var msg = charset.decode(ByteBuffer.wrap(bytes));
			return msg == null || msg.length() == 0 ? Optional.empty() : Optional.of(msg);
		}

		@Override
		public Optional<Supplier<CharSequence>> thrown() {
			var t = this.delegate.getThrown();
			if (t == null) {
				return Optional.empty();
			}
			return Optional.of(() -> Factory.toCharSequence(t));
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

	/**
	 * Builds {@link Log4j2Appender} instances.
	 *
	 * @param <B> The type to build
	 */
	public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<Log4j2Appender> {

		@PluginBuilderAttribute
		private Synchronicity synchronicity;
		@PluginBuilderAttribute
		private Severity flushSeverity;
		@PluginElement("Decorators")
		private DecoratorItem[] decorators;

		private LoggingOptions loggingOptions;
		private MonitoredResource monitoredResource;

		/**
		 * @param synchronicity the write synchronicity to set.
		 * @return this builder.
		 */
		public B setSynchronicity(Synchronicity synchronicity) {
			this.synchronicity = synchronicity;
			return asBuilder();
		}

		/**
		 * @param flushSeverity the flush severity to set.
		 * @return this builder.
		 */
		public B setFlushSeverity(Severity flushSeverity) {
			this.flushSeverity = flushSeverity;
			return asBuilder();
		}

		/**
		 * @param decorators the decorators to set.
		 * @return this builder.
		 */
		public B setDecorators(DecoratorItem... decorators) {
			this.decorators = decorators;
			return asBuilder();
		}

		/**
		 * @param loggingOptions the logging options to set.
		 * @return this builder.
		 */
		public B setLoggingOptions(LoggingOptions loggingOptions) {
			this.loggingOptions = loggingOptions;
			return asBuilder();
		}

		/**
		 * @param monitoredResource the monitored instance to set.
		 * @return this builder.
		 */
		public B setMonitoredResource(MonitoredResource monitoredResource) {
			this.monitoredResource = monitoredResource;
			return asBuilder();
		}

		@Override
		public Log4j2Appender build() {
			return new Log4j2Appender(this);
		}
	}
}
