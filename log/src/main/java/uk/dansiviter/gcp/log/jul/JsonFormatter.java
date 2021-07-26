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

import static uk.dansiviter.juli.AbstractHandler.property;
import static java.util.logging.LogManager.getLogManager;
import static uk.dansiviter.gcp.log.Factory.logEntry;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import com.google.cloud.logging.LogEntry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import uk.dansiviter.gcp.log.EntryDecorator;
import uk.dansiviter.gcp.log.Factory;
import uk.dansiviter.gcp.log.jul.JulHandler.JulEntry;

/**
 * This formatter can be used if you want to leverage the automatic container persistence.
 */
public class JsonFormatter extends ExpandingFormatter {
	private static final Method TO_PB;

	static {
		try {
			var toPb = LogEntry.class.getDeclaredMethod("toPb", String.class);
			if (!toPb.trySetAccessible()) {
				throw new IllegalStateException();
			}
			TO_PB = toPb;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	private final List<EntryDecorator> decorators = new LinkedList<>();
	private final Formatter delegate = new BasicFormatter();

	/**
	 *
	 */
	public JsonFormatter() {
		property(getLogManager(), getClass(), "decorators").map(Factory::decorators).ifPresent(this.decorators::addAll);
	}

	@Override
	protected String doFormat(LogRecord record) {
		var entry = logEntry(new JulEntry(record, this.delegate), this.decorators);
		try {
			var pbEntry = (com.google.logging.v2.LogEntry) TO_PB.invoke(entry, "foo");
			return JsonFormat.printer().omittingInsignificantWhitespace().print(pbEntry);
		} catch (ReflectiveOperationException | InvalidProtocolBufferException e) {
			throw new IllegalStateException(e);
		}
	}
}
