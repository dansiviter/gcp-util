
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

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingEnhancer;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Severity;

/**
 * XXX Investigate low GC thread-local builders. It appears the LogEntry.Builder
 * has no ability to clear.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Dec 2019]
 */
public enum Factory { ;

	/**
	 *
	 * @param entry
	 * @param decorators
	 * @return
	 */
	@Nonnull
	public static LogEntry logEntry(@Nonnull Entry entry, @Nonnull List<EntryDecorator> decorators) {
		final Map<String, Object> payload = payload(entry);

		final LogEntry.Builder b = LogEntry.newBuilder(null).setTimestamp(entry.timestamp())
				.setSeverity(entry.severity());
		entry.logName().ifPresent(t -> b.addLabel("logName", t.toString()));
		entry.threadName().ifPresent(t -> b.addLabel("thread", t.toString()));
		entry.mdc().forEach((k, v) -> b.addLabel(k, Objects.toString(v)));
		decorators.forEach(d -> d.decorate(b, entry, payload));

		b.setPayload(JsonPayload.of(payload));

		return b.build();
	}

	/**
	 * Converts comma separated list of {@link EntryDecorator} class names into
	 * instances.
	 *
	 * @param decorators
	 * @return
	 */
	@Nonnull
	public static List<EntryDecorator> decorators(@Nonnull String decorators) {
		if (decorators.isBlank()) {
			return emptyList();
		}
		return stream(decorators.split(",")).map(d -> {
			return instance(EntryDecorator.class, d);
		}).collect(toList());
	}

	/**
	 * Converts legacy {@link LoggingEnhancer} class names into decorators. This is
	 * useful for things like the OpenCensus trace log correlaton enhancers.
	 *
	 * @param enhancers
	 * @return
	 */
	@Nonnull
	public static List<EntryDecorator> enhancers(@Nonnull String enhancers) {
		if (enhancers.isBlank()) {
			return emptyList();
		}
		return stream(enhancers.split(",")).map(d -> {
			return instance(LoggingEnhancer.class, d);
		}).map(EntryDecorator::decorator).collect(toList());
	}

	/**
	 *
	 * @param entry
	 * @return
	 */
	@Nonnull
	private static Map<String, Object> payload(Entry entry) {
		final Map<String, Object> data = new HashMap<>();

		entry.message().ifPresent(m -> data.put("message", m));

		final Map<String, Object> context = new HashMap<>();
		if (entry.severity().ordinal() >= Severity.WARNING.ordinal() && entry.thrown().isPresent()) {
			entry.source().ifPresent(s -> {
				context.put("reportLocation", s.asMap());
			});
		}

		if (!context.isEmpty()) {
			data.put("context", context);
		}

		return data;
	}

	/**
	 *
	 * @param t
	 * @return
	 */
	@Nonnull
	public static CharSequence toCharSequence(@Nonnull Throwable t) {
		try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
			t.printStackTrace(pw);
			return sw.getBuffer();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 *
	 * @param <T>
	 * @param type
	 * @param name
	 * @return
	 */
	public @Nonnull static <T> T instance(@Nonnull Class<T> type, String name) {
		try {
			final Class<?> concreteCls = ClassLoader.getSystemClassLoader().loadClass(name);
			return type.cast(concreteCls.getDeclaredConstructor().newInstance());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException
				| NoSuchMethodException e) {
			throw new IllegalArgumentException("Unable to create! [name]", e);
		}
	}

	/**
	 *
	 * @param <T>
	 * @param type
	 * @param name
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public @Nonnull static <T> T instance(@Nonnull String name) {
		try {
			final Class<?> concreteCls = Class.forName(name);
			return (T) concreteCls.getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| InvocationTargetException | NoSuchMethodException e)
		{
			throw new IllegalArgumentException(format("Unable to create! [s]", name), e);
		}
	}
}
