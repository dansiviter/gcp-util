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
package uk.dansiviter.gcp.monitoring.log.opentracing;

import static com.google.cloud.ServiceOptions.getDefaultProjectId;
import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.cloud.logging.LogEntry.Builder;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import uk.dansiviter.gcp.monitoring.log.Entry;
import uk.dansiviter.gcp.monitoring.log.EntryDecorator;

/**
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class Decorator implements EntryDecorator {
	private final String prefix;
	private final Supplier<Tracer> tracer;

	/**
	 * Creates a using {@link GlobalTracer#get()}
	 */
	public Decorator() {
		this(GlobalTracer::get);
	}

	/**
	 * Attempts to auto-detect the project ID.
	 *
	 * @param tracer the tracer supplier.
	 */
	public Decorator(@Nonnull Supplier<Tracer> tracer) {
		this(tracer, getDefaultProjectId());
	}

	/**
	 *
	 * @param tracer the tracer supplier.
	 * @param projectId the project identifier.
	 */
	public Decorator(@Nonnull Supplier<Tracer> tracer, @Nonnull String projectId) {
		this.prefix = String.format("projects/%s/traces/", projectId);
		this.tracer = requireNonNull(tracer);
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		var span = this.tracer.get().activeSpan();
		if (span == null) {
			return;
		}

		var spanCtx = span.context();
		b.setTrace(this.prefix.concat(spanCtx.toTraceId()));
		b.setSpanId(spanCtx.toSpanId());
		b.setTraceSampled(true);
	}
}