/*
 * Copyright 2019-2020 Daniel Siviter
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
package uk.dansiviter.stackdriver.log.log4j2;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;
import com.google.cloud.logging.Logging.WriteOption;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import uk.dansiviter.stackdriver.ResourceType;
import uk.dansiviter.stackdriver.log.Entry;
import uk.dansiviter.stackdriver.log.EntryDecorator;
import uk.dansiviter.stackdriver.log.Factory;

/**
 * A Log4J v2 implementation of {@link Appender}.
 *
 * <pre>
 * &lt;Configuration status="WARN"&gt;
 *   &lt;Appenders&gt;
 *     &lt;Stackdriver name="java.log" synchronicity="ASYNC"
 *         enhancers="io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer"&gt;
 *       &lt;Filter .../&gt;
 *     &lt;/Stackdriver&gt;
 *   &lt;/Appenders&gt;
 *   ...
 * &lt;/Configuration&gt;
 * </pre>
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
@Plugin(name = "Stackdriver", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class Log4j2Appender extends AbstractAppender {
	private final LoggingOptions loggingOptions;
	private final WriteOption[] defaultWriteOptions;
	private final List<EntryDecorator> decorators = new LinkedList<>();

	private volatile Logging logging;

	protected Log4j2Appender(
			@Nonnull String name,
			Optional<Filter> filter,
			boolean ignoreExceptions,
			Optional<Synchronicity> synchronicity,
			Optional<Severity> flushSeverity,
			Optional<String> decorators,
			Optional<String> enhancers,
			@Nonnull LoggingOptions loggingOptions,
			@Nonnull MonitoredResource monitoredResource)
	{
		super(
				name,
				filter.orElse(null),
				PatternLayout.createDefaultLayout(),
				ignoreExceptions,
				Property.EMPTY_ARRAY);

		this.loggingOptions = loggingOptions;

		synchronicity.ifPresent(logging()::setWriteSynchronicity);
		flushSeverity.ifPresent(logging()::setFlushSeverity);
		decorators.map(Factory::decorators).ifPresent(this.decorators::addAll);
		enhancers.map(Factory::enhancers).ifPresent(this.decorators::addAll);

		this.defaultWriteOptions = new WriteOption[] {
			WriteOption.logName(name),
			WriteOption.resource(monitoredResource)
		};
	}

	/**
	 *
	 */
	private Logging logging() {
		if (this.logging == null) {
			synchronized (this) {
				if (this.logging == null) {
					this.logging = this.loggingOptions.getService();
				}
			}
		}
		return this.logging;
	}

	@Override
	public void append(LogEvent event) {
		final LogEntry entry;
		try {
			entry = Factory.logEntry(new Log4J2Entry(event), this.decorators);
		} catch (RuntimeException e) {
			error(e.getLocalizedMessage(), e);
			return;
		}
		try {
			logging().write(List.of(entry), this.defaultWriteOptions);
		} catch (RuntimeException e) {
			error(e.getLocalizedMessage(), e);
		}
	}

	@Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        boolean stopped = super.stop(timeout, timeUnit, false);

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
	 *
	 * @param name
	 * @param filter
	 * @param layout
	 * @param ignoreExceptions
	 * @param synchronicity
	 * @param flushSeverity
	 * @param decorators
	 * @param enhancers
	 * @return
	 */
	@PluginFactory
	public static Log4j2Appender createAppender(
		@PluginAttribute("name") @Nonnull String name,
		@PluginElement("Filters") Filter filter,
		@PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
		@PluginAttribute("synchronicity") Synchronicity synchronicity,
		@PluginAttribute("synchronicity") Severity flushSeverity,
		@PluginAttribute("decorators") String decorators,
		@PluginAttribute("enhancers") String enhancers)
	{
		return createAppender(
			name,
			Optional.of(filter),
			ignoreExceptions,
			Optional.of(synchronicity),
			Optional.of(flushSeverity),
			Optional.of(decorators),
			Optional.of(enhancers),
			LoggingOptions.getDefaultInstance(),
			ResourceType.autoDetect().monitoredResource());
	}

	/**
	 *
	 * @param name
	 * @param filter
	 * @param layout
	 * @param ignoreExceptions
	 * @param synchronicity
	 * @param flushSeverity
	 * @param decorators
	 * @param enhancers
	 * @param loggingOptions
	 * @param monitoredResource
	 * @return
	 */
	public static Log4j2Appender createAppender(
		@Nonnull String name,
		Optional<Filter> filter,
		boolean ignoreExceptions,
		Optional<Synchronicity> synchronicity,
		Optional<Severity> flushSeverity,
		Optional<String> decorators,
		Optional<String> enhancers,
		@Nonnull LoggingOptions loggingOptions,
		@Nonnull MonitoredResource monitoredResource)
	{
		return new Log4j2Appender(
			name,
			filter,
			ignoreExceptions,
			synchronicity,
			flushSeverity,
			decorators,
			enhancers,
			loggingOptions,
			monitoredResource);
	}

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
	private class Log4J2Entry implements Entry {
		private final LogEvent delegate;

		Log4J2Entry(LogEvent delegate) {
			this.delegate = delegate;
		}

		@Override
		public Severity severity() {
			return Log4j2Appender.severity(this.delegate.getLevel());
		}

		@Override
		public long timestamp() {
			return this.delegate.getTimeMillis();
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
		public Optional<? super CharSequence> message() {
			byte[] bytes = getLayout().toByteArray(this.delegate);
			Charset charset;
			if (getLayout() instanceof StringLayout) {
				charset = ((StringLayout) getLayout()).getCharset();
			} else {
				charset = Charset.defaultCharset();
			}
			return Optional.of(charset.decode(ByteBuffer.wrap(bytes)));
		}

		@Override
		public Optional<Supplier<? super CharSequence>> thrown() {
			final Throwable t = this.delegate.getThrown();
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
}
