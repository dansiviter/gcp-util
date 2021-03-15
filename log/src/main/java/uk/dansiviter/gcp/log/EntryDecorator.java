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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

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
	 * Wraps a {@link LoggingEnhancer} as a {@link EntryDecorator}.
	 *
	 * @param enhancer the enhancer to wrap.
	 * @return the wrapped enhancer.
	 */
	public static EntryDecorator decorator(@Nonnull LoggingEnhancer enhancer) {
		requireNonNull(enhancer);
		return (b, e, p) -> enhancer.enhanceLogEntry(b);
	}

	/**
	 * Append the {@code serviceContext} element using {@link Class}.
	 *
	 * @param cls the class to use.
	 * @return a new decorator.
	 */
	public static EntryDecorator serviceContext(@Nonnull Class<?> cls) {
		return serviceContext(cls.getPackage());
	}

	/**
	 * Append the {@code serviceContext} element using {@link Package}.
	 *
	 * @param pkg the package to use.
	 * @return a new decorator.
	 */
	public static EntryDecorator serviceContext(@Nonnull Package pkg) {
		if (pkg.getImplementationTitle() == null || pkg.getImplementationVersion() == null) {
			return (b, e, p) -> { };
		}
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

	/**
	 * Appends {@code mdc} using given {@link Map}.
	 *
	 * @param mdcSupplier the supplier.
	 * @return a decorator instance.
	 */
	public static EntryDecorator mdc(@Nonnull Supplier<Map<String, ?>> mdcSupplier) {
		return (b, e, p) -> {
			Map<String, ?> mdc = mdcSupplier.get();
			if (mdc.isEmpty()) {
				return;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) p.computeIfAbsent("mdc", k -> new HashMap<>());
			mdc.forEach((k, v) -> map.put(k, Objects.toString(v)));
		};
	}

	/**
	 *
	 * @param decorator the first decorator.
	 * @param decorators subsequent decorators.
	 * @return a decorator that wraps the others.
	 */
	public static EntryDecorator all(@Nonnull EntryDecorator decorator, EntryDecorator... decorators) {
		return (b, e, p) -> {
			decorator.decorate(b, e, p);
			for (EntryDecorator d : decorators) {
				d.decorate(b, e, p);
			}
		};
	}
}
