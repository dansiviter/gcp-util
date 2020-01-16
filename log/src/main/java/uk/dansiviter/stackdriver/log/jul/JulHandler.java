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
package uk.dansiviter.stackdriver.log.jul;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.WriteOption;
import com.google.cloud.logging.LoggingEnhancer;
import com.google.cloud.logging.LoggingLevel;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;

import uk.dansiviter.stackdriver.ResourceType;
import uk.dansiviter.stackdriver.log.Entry;
import uk.dansiviter.stackdriver.log.EntryDecorator;
import uk.dansiviter.stackdriver.log.Factory;

/**
 * Inspired by {@link com.google.cloud.logging.LoggingHandler} but one major limitation is it's use of
 * {@link com.google.cloud.logging.Payload.StringPayload} which heavily limits the data that can be utilised by GCP.
 * </p>
 * Example file {@code java.util.logging.config.file} config:
 * <pre>
 * .level=INFO
 * handlers=uk.dansiviter.stackdriver.log.JulHandler
 *
 * uk.dansiviter.stackdriver.log.JulHandler.level=FINEST
 * uk.dansiviter.stackdriver.log.JulHandler.filter=foo.MyFilter
 * uk.dansiviter.stackdriver.log.JulHandler.decorators=foo.MyDecorator
 * uk.dansiviter.stackdriver.log.JulHandler.enhancers=io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer
 *
 * java.util.logging.SimpleFormatter.format=%3$s: %5$s%6$s
 * </pre>
 * ...of by via class {@code java.util.logging.config.class}:
 * <pre>
 * public class MyConfig {
 *   public MyConfig() {
 *     final JulHandler handler = new JulHandler();
 *     handler.setFilter(new MyFilter());
 *     handler.add(new MyDecorator()).add(new OpenCensusTraceLoggingEnhancer());
 *
 *     final Logger root = Logger.getLogger("");
 *     root.setLevel(Level.INFO);
 *     root.addHandler(handler);
 *   }
 * }
 * </pre>
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 * @see com.google.cloud.logging.LoggingHandler
 */
public class JulHandler extends Handler {
	private final LogManager logManager;
	private final LoggingOptions loggingOptions;
	private final WriteOption[] defaultWriteOptions;
	private final List<EntryDecorator> decorators = new LinkedList<>();

	private volatile Logging logging;

	/**
	 *
	 */
	public JulHandler() {
		this(Optional.empty(), LoggingOptions.getDefaultInstance(), ResourceType.autoDetect().monitoredResource());
	}

	/**
	 *
	 * @param logName
	 * @param loggingOptions
	 * @param monitoredResource
	 */
	private JulHandler(
		Optional<String> logName,
		@Nonnull LoggingOptions loggingOptions,
		@Nonnull MonitoredResource monitoredResource)
	{
		try {
			this.logManager = LogManager.getLogManager();
			this.loggingOptions = loggingOptions;

			property("filter").map(Factory::<Filter>instance).ifPresent(this::setFilter);
			final Formatter formatter = property("filter").map(Factory::<Formatter>instance).orElseGet(() -> new BasicFormatter());
			setFormatter(formatter);
			final Level level = property("level").map(Level::parse).orElse(Level.INFO);
			setLevel(level);
			final Severity flushSeverity = property("flushSeverity").map(Severity::valueOf).orElse(Severity.WARNING);
			setFlushSeverity(flushSeverity);
			final Synchronicity synchronicity = property("synchronicity").map(Synchronicity::valueOf).orElse(Synchronicity.ASYNC);
			setSynchronicity(synchronicity);
			property("decorators").map(Factory::decorators).ifPresent(this.decorators::addAll);
			property("enhancers").map(Factory::enhancers).ifPresent(this.decorators::addAll);

			this.defaultWriteOptions = new WriteOption[] {
				WriteOption.logName(logName.orElse("java.log")),
				WriteOption.resource(monitoredResource)
			};
		} catch (RuntimeException e) {
			reportError(null, e, ErrorManager.OPEN_FAILURE);
			throw e;
		}
	}

	/**
	 * For JBoss LogManager
	 */
	public JulHandler setFlushSeverity(Severity flushSeverity) {
		logging().setFlushSeverity(flushSeverity);
		return this;
	}

	/**
	 * For JBoss LogManager
	 */
	public JulHandler setSynchronicity(Synchronicity synchronicity) {
		logging().setWriteSynchronicity(synchronicity);
		return this;
	}

	/**
	 * For JBoss LogManager
	 */
	public JulHandler setDecorators(String decorators) {
		this.decorators.addAll(Factory.decorators(decorators));
		return this;
	}

	/**
	 * For JBoss LogManager
	 */
	public JulHandler setEnhancers(String enhancers) {
		this.decorators.addAll(Factory.enhancers(enhancers));
		return this;
	}

	/**
	 *
	 * @param decorators
	 * @return
	 */
	public JulHandler add(EntryDecorator... decorators) {
		this.decorators.addAll(asList(decorators));
		return this;
	}

	/**
	 *
	 * @param enhancers
	 * @return
	 */
	public JulHandler add(LoggingEnhancer... enhancers) {
		return add(stream(enhancers).map(EntryDecorator::decorator).toArray(EntryDecorator[]::new));
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
	public void publish(LogRecord record) {
		if (!isLoggable(record)) {
			return;
		}

		final LogEntry entry;
		try {
			entry = Factory.logEntry(new JavaUtilEntry(record), this.decorators);
		} catch (RuntimeException e) {
			reportError(e.getLocalizedMessage(), e, ErrorManager.FORMAT_FAILURE);
			return;
		}
		try {
			logging().write(List.of(entry), this.defaultWriteOptions);
		} catch (RuntimeException e) {
			reportError(e.getLocalizedMessage(), e, ErrorManager.WRITE_FAILURE);
		}
	}

	@Override
	public void flush() {
		if (this.logging != null) {
			try {
				this.logging.flush();
			} catch (Exception e) {
				reportError("Unable to flush!", e, ErrorManager.FLUSH_FAILURE);
			}
		}
	}

	@Override
	public void close() throws SecurityException {
		flush();
		if (this.logging != null) {
			try {
				this.logging.close();
			} catch (Exception e) {
				reportError("Unable to close!", e, ErrorManager.CLOSE_FAILURE);
			}
		}
	}

	/**
	 *
	 * @param <T>
	 * @param logManager
	 * @param name
	 * @return
	 */
	private Optional<String> property(@Nonnull String name) {
		return Optional.ofNullable(this.logManager.getProperty(JulHandler.class.getName() + "." + name));
	}


	// --- Static Methods ---

	/**
	 *
	 * @param resource
	 * @return
	 */
	public static JulHandler julHandler(@Nonnull MonitoredResource resource) {
		return new JulHandler(Optional.empty(), LoggingOptions.getDefaultInstance(), resource);
	}

	/**
	 *
	 * @param loggingOptions
	 * @param resource
	 * @return
	 */
	public static JulHandler julHandler(@Nonnull LoggingOptions loggingOptions, @Nonnull MonitoredResource resource) {
		return new JulHandler(Optional.empty(), loggingOptions, resource);
	}

	/**
	 *
	 * @param logName
	 * @param loggingOptions
	 * @param resource
	 * @return
	 */
	public static JulHandler julHandler(@Nonnull String logName, @Nonnull LoggingOptions loggingOptions, @Nonnull MonitoredResource resource) {
		return new JulHandler(Optional.of(logName), loggingOptions, resource);
	}

	/**
	 *
	 * @param level
	 * @return
	 */
	private static Severity severity(Level level) {
		if (level instanceof LoggingLevel) {
			return ((LoggingLevel) level).getSeverity();
		}
		if (Level.SEVERE.equals(level)) {
			return Severity.ERROR;
		} else if (Level.WARNING.equals(level)) {
			return Severity.WARNING;
		} else if (Level.INFO.equals(level)) {
			return Severity.INFO;
		} else if (Level.FINE.equals(level) || Level.FINER.equals(level) || Level.FINEST.equals(level)) {
			return Severity.DEBUG;
		}
		return Severity.DEFAULT;
	}


	// --- Inner Classes ---

	/**
	 *
	 */
	private class JavaUtilEntry implements Entry {
		private final LogRecord delegate;

		JavaUtilEntry(LogRecord delegate) {
			this.delegate = delegate;
		}

		@Override
		public Severity severity() {
			return JulHandler.severity(this.delegate.getLevel());
		}

		@Override
		public long timestamp() {
			return this.delegate.getMillis();
		}

		@Override
		public Optional<Source> source() {
			return Optional.of(new Source() {
				@Override
				public String className() {
					return delegate.getSourceClassName();
				}

				@Override
				public String method() {
					return delegate.getSourceMethodName();
				}
			});
		}

		@Override
		public Optional<? super CharSequence> message() {
			final String msg = getFormatter().format(this.delegate);
			return msg.isEmpty() ? Optional.empty() : Optional.of(msg);
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
			// JUL logging is a synchronous implementation so this should be correct!
			final Thread thread = Thread.currentThread();
			if (thread.getId() == (long) this.delegate.getThreadID()) {
				return Optional.of(thread.getName());
			}
			return Optional.empty();
		}
	}
}
