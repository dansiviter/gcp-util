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
package uk.dansiviter.gcp.log;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.logging.Severity;

/**
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public interface Entry {
	/**
	 * @return log severity.
	 */
	@Nonnull Severity severity();

	/**
	 * @return timestamp from epoch.
	 */
	long timestamp();

	/**
	 * @return source of log message.
	 */
	default Optional<Source> source() {
		return Optional.empty();
	}

	/**
	 * @return formatted log message.
	 */
	default Optional<CharSequence> message() {
		return Optional.empty();
	}

	/**
	 * @return stacktrace as a string.
	 */
	default Optional<Supplier<CharSequence>> thrown(){
		return Optional.empty();
	}

	/**
	 * @return log name. Often the class where the log occured.
	 */
	default Optional<CharSequence> logName() {
		return Optional.empty();
	}

	/**
	 * @return the thread where the log message originated.
	 */
	default Optional<CharSequence> threadName() {
		return Optional.empty();
	}

	/**
	 * The source of the log entry.
	 */
	public interface Source {
		/**
		 * @return the class name.
		 */
		@Nonnull String className();

		/**
		 * @return the line of the code, if available.
		 */
		default OptionalInt line() {
			return OptionalInt.empty();
		}

		/**
		 * @return the method name.
		 */
		@Nonnull String method();

		/**
		 * @return the values as a map.
		 * @see <a href="https://cloud.google.com/error-reporting/docs/formatting-error-messages#json_representation">JSON Representation</a>
		 */
		default Map<String, Object> asMap() {
			var map = new HashMap<String, Object>();
			map.put("filePath", className());
			map.put("functionName", method());
			line().ifPresent(i -> map.put("lineNumber", i));
			return map;
		}
	}
}
