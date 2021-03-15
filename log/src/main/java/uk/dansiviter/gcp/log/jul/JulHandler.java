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

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;
import static uk.dansiviter.gcp.log.Factory.logEntry;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.WriteOption;
import com.google.cloud.logging.LoggingEnhancer;
import com.google.cloud.logging.LoggingLevel;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;

import uk.dansiviter.gcp.AtomicInit;
import uk.dansiviter.gcp.ResourceType;
import uk.dansiviter.gcp.log.Entry;
import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.Factory;
import uk.dansiviter.juli.AsyncHandler;

/**
 * Inspired by {@link com.google.cloud.logging.LoggingHandler} but one major limitation is it's use of
 * {@link com.google.cloud.logging.Payload.StringPayload} which heavily limits the data that can be utilised by GCP.
 * <p>
 * <b>Configuration:</b>
 * In addition to the configuration defined in {@link AsyncHandler}:
 * <ul>
 * <li>   {@code &lt;handler-name&gt;.flushSeverity}
 *        specifies the severity level which mandates a flush.
 *        (defaults to {@link Severity#WARNING WARNING})
 * <li>   {@code &lt;handler-name&gt;.synchronicity}
 * 		  specifies the synchronicity of message writing.
 * 		  (defaults to {@link Synchronicity#ASYNC ASYNC})
 * <li>   {@code &lt;handler-name&gt;.decorators}
 *        comma-separated list of fully qualified class names of either {@link EntryDecorator} or
 *        {@link com.google.cloud.logging.LoggingEnhancer LoggingEnhancer} to perform decoration of log entries.
 *        (defaults to empty list)</li>
 * </ul>
 * Example file {@code java.util.logging.config.file} config:
 *
 * <pre>
 * .level=INFO
 * handlers=uk.dansiviter.gcp.log.jul.JulHandler
 *
 * uk.dansiviter.gcp.log.jul.JulHandler.level=FINEST
 * uk.dansiviter.gcp.log.jul.JulHandler.filter=foo.MyFilter
 * uk.dansiviter.gcp.log.jul.JulHandler.decorators=uk.dansiviter.gcp.log.opentelemetry.Decorator
 *
 * java.util.logging.SimpleFormatter.format=%3$s: %5$s%6$s
 * </pre>
 *
 * ...of by via class {@code java.util.logging.config.class}:
 *
 * <pre>
 * public class MyConfig {
 * 	public MyConfig() {
 * 		final JulHandler handler = new JulHandler();
 * 		handler.setFilter(new MyFilter());
 * 		handler.add(new Decorator());
 *
 * 		final Logger root = Logger.getLogger("");
 * 		root.setLevel(Level.INFO);
 * 		root.addHandler(handler);
 * 	}
 * }
 * </pre>
 *
 * Note: generally there is no need to specify a {@link Formatter} as this will
 * be taken care by {@link BasicFormatter}.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 * @see com.google.cloud.logging.LoggingHandler
 */
public class JulHandler extends AsyncHandler {
	private final List<EntryDecorator> decorators = new LinkedList<>();
	private final LoggingOptions loggingOptions;
	private final AtomicInit<Logging> logging;
	private final WriteOption[] defaultWriteOptions;

	private Severity flushSeverity;
	private Synchronicity synchronicity;

	/**
	 *
	 */
	public JulHandler() {
		this(Optional.empty(), LoggingOptions.getDefaultInstance(), ResourceType.monitoredResource());
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
			var manager = requireNonNull(LogManager.getLogManager());
			this.loggingOptions = requireNonNull(loggingOptions);
			this.logging = new AtomicInit<>(() -> {
				if (this.closed.get()) {
					throw new IllegalStateException("Handler already closed!");
				}
				var instance = this.loggingOptions.getService();
				instance.setFlushSeverity(this.flushSeverity);
				instance.setWriteSynchronicity(this.synchronicity);
				return instance;
			});
			setFormatter(new BasicFormatter());
			Severity flushSeverity = property(manager, "flushSeverity").map(Severity::valueOf).orElse(Severity.WARNING);
			setFlushSeverity(flushSeverity);
			Synchronicity synchronicity = property(manager, "synchronicity").map(Synchronicity::valueOf)
					.orElse(Synchronicity.ASYNC);
			setSynchronicity(synchronicity);
			property(manager, "decorators").map(Factory::decorators).ifPresent(this.decorators::addAll);

			this.defaultWriteOptions = new WriteOption[] { WriteOption.logName(logName.orElse("java.log")),
					WriteOption.resource(monitoredResource) };
		} catch (RuntimeException e) {
			reportError(null, e, ErrorManager.OPEN_FAILURE);
			throw e;
		}
	}

	/**
	 * For JBoss LogManager
	 */
	public JulHandler setFlushSeverity(Severity flushSeverity) {
		this.flushSeverity = flushSeverity;
		if (this.logging.isInitialised()) {
			logging().setFlushSeverity(flushSeverity);
		}
		return this;
	}

	/**
	 * For JBoss LogManager
	 */
	public JulHandler setSynchronicity(Synchronicity synchronicity) {
		this.synchronicity = synchronicity;
		if (this.logging.isInitialised()) {
			logging().setWriteSynchronicity(synchronicity);
		}
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
	 * @param decorators the decorators to add.
	 * @return the handler instance.
	 */
	public JulHandler add(EntryDecorator... decorators) {
		this.decorators.addAll(asList(decorators));
		return this;
	}

	/**
	 * @param enhancers the enhancers to add.
	 * @return the handler instance.
	 */
	public JulHandler add(LoggingEnhancer... enhancers) {
		return add(stream(enhancers).map(EntryDecorator::decorator).toArray(EntryDecorator[]::new));
	}

	private Logging logging() {
		return this.logging.get();
	}

	@Override
	protected void doPublish(LogRecord record) {
		var entry = new JulEntry(record);

		try {
			logging().write(Collections.singleton(logEntry(entry, this.decorators)), this.defaultWriteOptions);
		} catch (RuntimeException e) {
			reportError(e.getLocalizedMessage(), e, WRITE_FAILURE);
			return;
		}
	}

	@Override
	public void flush() {
		this.logging.ifInitialised(l -> {
			try {
				l.flush();
			} catch (Exception e) {
				reportError("Unable to flush!", e, FLUSH_FAILURE);
			}
		});
	}

	@Override
	public void close() {
		super.close();
		flush();
		try {
			this.logging.close();
		} catch (Exception e) {
			reportError("Unable to close!", e, CLOSE_FAILURE);
		}
	}


	// --- Static Methods ---

	/**
	 * Create handler instance.
	 *
	 * @param resource the resource instance to use.
	 * @return the handler instance.
	 */
	public static JulHandler julHandler(@Nonnull MonitoredResource resource) {
		return new JulHandler(Optional.empty(), LoggingOptions.getDefaultInstance(), resource);
	}

	/**
	 * Create handler instance.
	 *
	 * @param loggingOptions the logging options instance.
	 * @param resource the resource instance to use.
	 * @return the handler instance.
	 */
	public static JulHandler julHandler(@Nonnull LoggingOptions loggingOptions, @Nonnull MonitoredResource resource) {
		return new JulHandler(Optional.empty(), loggingOptions, resource);
	}

	/**
	 * Create handler instance.
	 *
	 * @param logName the log name.
	 * @param loggingOptions the logging options instance.
	 * @param resource the resource instance to use.
	 * @return the handler instance.
	 */
	public static JulHandler julHandler(@Nonnull String logName, @Nonnull LoggingOptions loggingOptions, @Nonnull MonitoredResource resource) {
		return new JulHandler(Optional.of(logName), loggingOptions, resource);
	}

	/**
	 * Convert level to severity equivalent.
	 *
	 * @param level the level to convert.
	 * @return the severity equivalent or {@link Severity#DEFAULT} if not.
	 */
	private static Severity severity(@Nonnull Level level) {
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
	 * The JUL entry.
	 */
	private class JulEntry implements Entry {
		private final LogRecord delegate;

		JulEntry(LogRecord delegate) {
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
			if (delegate.getSourceClassName() == null || delegate.getSourceMethodName() == null) {
				return Optional.empty();
			}
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
		public Optional<CharSequence> message() {
			var msg = getFormatter().format(this.delegate);
			return msg == null || msg.isEmpty() ? Optional.empty() : Optional.of(msg);
		}

		@Override
		public Optional<Supplier<? super CharSequence>> thrown() {
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
			// JUL logging is a synchronous implementation so this should be correct!
			var thread = Thread.currentThread();
			if (thread.getId() == (long) this.delegate.getThreadID()) {
				return Optional.of(thread.getName());
			}
			return Optional.empty();
		}
	}
}
