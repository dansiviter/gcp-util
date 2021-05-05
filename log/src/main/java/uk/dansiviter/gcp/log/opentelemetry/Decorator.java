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
package uk.dansiviter.gcp.log.opentelemetry;

import static com.google.cloud.ServiceOptions.getDefaultProjectId;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import javax.annotation.Nonnull;

import com.google.cloud.logging.LogEntry.Builder;

import io.opentelemetry.api.trace.Span;
import uk.dansiviter.gcp.log.Entry;
import uk.dansiviter.gcp.log.EntryDecorator;

/**
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class Decorator implements EntryDecorator {
	private final String prefix;

	/**
	 * Attempts to auto-detect the project ID.
	 */
	public Decorator() {
		this(getDefaultProjectId());
	}

	/**
	 *
	 * @param projectId the project identifier.
	 */
	public Decorator(@Nonnull String projectId) {
		this.prefix = String.format("projects/%s/traces/", requireNonNull(projectId));
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		var spanCtx = Span.current().getSpanContext();
		if (!spanCtx.isValid()) {
			return;
		}
		b.setSpanId(spanCtx.getSpanId());
		b.setTrace(this.prefix.concat(spanCtx.getTraceId()));
		b.setTraceSampled(spanCtx.isSampled());
	}
}
