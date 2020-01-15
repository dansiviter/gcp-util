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
package uk.dansiviter.stackdriver.opentracing;

import static java.util.stream.Collectors.toList;
import static uk.dansiviter.stackdriver.ResourceType.Label.PROJECT_ID;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.cloud.trace.v2.TraceServiceClient;
import com.google.devtools.cloudtrace.v2.ProjectName;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import uk.dansiviter.stackdriver.ResourceType;
import uk.dansiviter.stackdriver.opentracing.propagation.B3MultiPropagator;
import uk.dansiviter.stackdriver.opentracing.propagation.Propagator;
import uk.dansiviter.stackdriver.opentracing.sampling.Sampler;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class StackdriverTracer implements Tracer, Closeable {
	private final StackdriverScopeManager scopeManager = new StackdriverScopeManager();
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final BlockingQueue<StackdriverSpan> spans = new LinkedBlockingQueue<>();
	private final Map<Format<?>, Propagator<?>> propagators = new HashMap<>();

	private final MonitoredResource resource;
	private final ProjectName projectName;
	private final TraceServiceClient client;
	private final Factory factory;
	private final Sampler sampler;

	StackdriverTracer(final Builder builder) {
		this.resource = builder.resource.orElse(ResourceType.autoDetect().monitoredResource());
		this.projectName = ProjectName.of(builder.projectId.orElse(ResourceType.get(this.resource, PROJECT_ID).get()));
		this.client = builder.client.orElse(defaultTraceServiceClient());
		this.propagators.putAll(builder.propegators);
		this.sampler = builder.sampler.orElse(Sampler.parentOverriding(Sampler.never()));
		this.factory = new Factory(this.resource);
		this.executor.scheduleAtFixedRate(this::flush, 10, 10, TimeUnit.SECONDS);
	}

	@Override
	public StackdriverScopeManager scopeManager() {
		return this.scopeManager;
	}

	@Override
	public Span activeSpan() {
		final Scope scope = this.scopeManager.active();
    	return scope == null ? null : scope.span();
	}

	@Override
	public SpanBuilder buildSpan(@Nonnull String operationName) {
		return StackdriverSpan.builder(operationName, this);
	}

	@Override
	public <C> void inject(@Nonnull SpanContext spanContext, @Nonnull Format<C> format, @Nonnull C carrier) {
		propagator(format).inject((StackdriverSpanContext) spanContext, carrier);
	}

	@Override
	public <C> StackdriverSpanContext extract(@Nonnull Format<C> format, @Nonnull C carrier) {
		return propagator(format).extract(carrier);
	}

	@Override
	public void close() {
		this.executor.shutdown();
		try {
			this.executor.awaitTermination(10, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			// FIXME log
			this.executor.shutdownNow();
		}
		flush();
		this.client.close();
	}

	private <C> Propagator<C> propagator(final Format<C> format) {
		@SuppressWarnings("unchecked")
		final Propagator<C> propagator = (Propagator<C>) this.propagators.get(format);
		if (propagator == null) {
			throw new IllegalStateException("No propagator found for format! [" + format + "]");
		}
		return propagator;
	}

	/**
	 *
	 */
	private void flush() {
		final List<StackdriverSpan> spans = new LinkedList<>();
		this.spans.drainTo(spans);
		if (spans.isEmpty()) {
			return;
		}
		final List<com.google.devtools.cloudtrace.v2.Span> converted =
				spans.stream().map(factory::toSpan).collect(toList());
		this.client.batchWriteSpans(this.projectName, converted);
	}

	/**
	 *
	 * @param span
	 */
	public void persist(final StackdriverSpan span) {
		this.spans.add(span);
	}

	/**
	 *
	 * @return
	 */
	Sampler sampler() {
		return this.sampler;
	}


	// --- Static Methods ---

	/**
	 *
	 * @return
	 */
	private static TraceServiceClient defaultTraceServiceClient() {
		try {
			return TraceServiceClient.create();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 *
	 * @return
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 *
	 * @param resource
	 * @return
	 */
	public static StackdriverTracer create(final MonitoredResource resource) {
		return builder().resource(resource).build();
	}

	/**
	 *
	 * @return
	 */
	private static Map<Format<?>, Propagator<?>> defaultPropagators() {
		return Map.of(Format.Builtin.HTTP_HEADERS, new B3MultiPropagator());
	}


	// --- Inner Classes ---

	/**
	 *
	 */
	public static class Builder {
		private final Map<Format<?>, Propagator<?>> propegators = new HashMap<>(defaultPropagators());
		private Optional<String> projectId = Optional.empty();
		private Optional<MonitoredResource> resource = Optional.empty();
		private Optional<TraceServiceClient> client = Optional.empty();
		private Optional<Sampler> sampler = Optional.empty();

		private Builder() { }

		/**
		 * @param client the client to set
		 */
		public Builder client(final TraceServiceClient client) {
			this.client = Optional.of(client);
			return this;
		}

		/**
		 * @param projectId the projectId to set
		 */
		public Builder projectId(final String projectId) {
			this.projectId = Optional.of(projectId);
			return this;
		}

		/**
		 * @param resource the resource to set
		 */
		public Builder resource(final MonitoredResource resource) {
			this.resource = Optional.of(resource);
			return this;
		}

		/**
		 *
		 * @param format
		 * @param propagator
		 * @return
		 */
		public Builder add(final Format<?> format, final Propagator<?> propagator) {
			this.propegators.put(format, propagator);
			return this;
		}

		/**
		 * @param sampler the sampler to set
		 */
		public Builder sampler(Sampler sampler) {
			this.sampler = Optional.of(sampler);
			return this;
		}

		/**
		 *
		 * @return
		 */
		public StackdriverTracer build() {
			return new StackdriverTracer(this);
		}
	}
}
