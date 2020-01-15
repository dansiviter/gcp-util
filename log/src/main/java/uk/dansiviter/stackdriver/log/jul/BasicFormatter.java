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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public class BasicFormatter extends Formatter {
	@Override
	public String format(LogRecord record) {
		final String message = formatMessage(record);

		if (record.getThrown() == null) {
			return message;
		}

		try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
			pw.write(message);
			pw.println();
			record.getThrown().printStackTrace(pw);
			return sw.toString();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
