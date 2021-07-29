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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.LogManager.getLogManager;
import static uk.dansiviter.gcp.log.Factory.logEntry;
import static uk.dansiviter.gcp.log.Factory.toJson;
import static uk.dansiviter.juli.JulUtil.property;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import javax.annotation.Nonnull;

import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.Factory;
import uk.dansiviter.gcp.log.jul.JulHandler.JulEntry;

/**
 * This formatter can be used if you want to leverage the automatic container logging. This is often simpler than using
 * direct Cloud Logger API integration. See <a href="https://cloud.google.com/logging/docs/structured-logging">Structured Logging</a>.
 *
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>   {@code &lt;handler-name&gt;.decorators}
 *        comma-separated list of fully qualified class names of either {@link EntryDecorator} or
 *        {@link com.google.cloud.logging.LoggingEnhancer LoggingEnhancer} to perform decoration of log entries.
 *        (defaults to empty list)</li>
 * </ul>
 *
 * Example file {@code java.util.logging.config.file} config:
 *
 * <pre>
 * .level=INFO
 * handlers=uk.dansiviter.juli.AsyncConsoleHandler
 *
 * uk.dansiviter.juli.AsyncConsoleHandler.level=FINEST
 * uk.dansiviter.juli.AsyncConsoleHandler.formatter=uk.dansiviter.gcp.log.JsonFormatter
 *
 * uk.dansiviter.gcp.log.JsonFormatter.decorators=uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator
 * </pre>
 */
public class JsonFormatter extends ExpandingFormatter {
	private final List<EntryDecorator> decorators = new LinkedList<>();
	private final Formatter delegate = new BasicFormatter();

	/**
	 *
	 */
	public JsonFormatter() {
		var manager = getLogManager();
		property(manager, getClass(), "decorators").ifPresent(this::setDecorators);
	}

	/**
	 * For JBoss LogManager
	 */
	public JsonFormatter setDecorators(@Nonnull String decorators) {
		this.decorators.clear();
		this.decorators.addAll(Factory.decorators(decorators));
		return this;
	}

	/**
	 * Add decorators.
	 *
	 * @param decorator at least one decorator must be provided.
	 * @param decorators any other decorators to add.
	 * @return this formatter instance.
	 */
	public JsonFormatter addDecorators(@Nonnull EntryDecorator decorator, EntryDecorator... decorators) {
		this.decorators.add(decorator);
		for (var d : decorators) {
			this.decorators.add(d);
		}
		return this;
	}

	@Override
	protected String doFormat(LogRecord record) {
		var entry = logEntry(new JulEntry(record, this.delegate), this.decorators);

		try (var os = new ByteArrayOutputStream()) {
			toJson(entry, os);
			return os.toString(UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
