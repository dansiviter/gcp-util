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
package uk.dansiviter.stackdriver.log;

import java.util.Map;

import javax.annotation.Nonnull;

import com.google.cloud.logging.LogEntry.Builder;
import com.google.cloud.logging.LoggingEnhancer;

/**
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
@FunctionalInterface
public interface EntryDecorator {
	/**
	 *
	 * @param b the log entry builder.
	 * @param e immutable log entry.
	 * @param payload mutable payload.
	 */
	void decorate(Builder b, Entry e, Map<String, Object> payload);

	/**
	 *
	 * @param enhancer
	 * @return
	 */
	public static EntryDecorator decorator(LoggingEnhancer enhancer) {
		return (b, e, p) -> enhancer.enhanceLogEntry(b);
	}

	/**
	 * Append the {@code serviceContext} element using {@link Package}.
	 *
	 * @param pkg the package to use.
	 * @return a new decorator.
	 */
	public static EntryDecorator serviceContext(@Nonnull Package pkg) {
		return serviceContext(pkg.getImplementationTitle(), pkg.getImplementationVersion());
	}

	/**
	 * Append the {@code serviceContext} using given values.
	 *
	 * @param service the service.
	 * @param version the version of the service.
	 * @return a new decorator.
	 */
	public static EntryDecorator serviceContext(String service, String version) {
		final Map<String, Object> serviceContext = Map.of(
						"service", service,
						"version", version);
		return (b, e, p) -> p.put("serviceContext", serviceContext);
	}
}
