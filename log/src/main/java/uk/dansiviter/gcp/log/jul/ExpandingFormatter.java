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

import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A formatter that will expand {@link Supplier} and {@link Optional} instances.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public abstract class ExpandingFormatter extends Formatter {
	@Override
	public final String format(LogRecord r) {
		// expand any suppliers so we can lazy extract values
		if (r.getParameters() != null) {
			var params = r.getParameters();
			for (int i = 0; i < params.length; i++) {
				if (params[i] instanceof Supplier) {
					params[i] = ((Supplier<?>) params[i]).get();
				}
				if (params[i] instanceof Optional) {
					params[i] = ((Optional<?>) params[i]).orElse(null);
				}
			}
		}
		return doFormat(r);
	}

	/**
	 * Perform the underlying log record formatting.
	 *
	 * @param r the record to format.
	 * @return the formatted string.
	 */
	protected abstract String doFormat(LogRecord r);
}
