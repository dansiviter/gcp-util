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
package uk.dansiviter.gcp.monitoring.opentelemetry;

import static java.util.stream.Collectors.toList;
import static uk.dansiviter.gcp.monitoring.ResourceType.Label.PROJECT_ID;
import static uk.dansiviter.gcp.monitoring.opentelemetry.Sampler.defaultSampler;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.MonitoredResource;
import com.google.cloud.trace.v2.TraceServiceClient;
import com.google.devtools.cloudtrace.v2.ProjectName;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import uk.dansiviter.gcp.monitoring.GaxUtil;
import uk.dansiviter.gcp.monitoring.ResourceType;

/**
 * A OpenTracing {@link Tracer} that pushes the traces to Clout Trace.
 *
 * @author Daniel Siviter
 * @since v1.0 [20 Feb 2020]
 */
public class CloudTracer implements Tracer, Closeable {
	private static final Logger LOG = Logger.getLogger(CloudTracer.class.getName());

	private final BlockingQueue<CloudTraceSpan> spans = new LinkedBlockingQueue<>();

	private final MonitoredResource resource;
	private final ProjectName projectName;
	private final TraceServiceClient client;
	private final Factory factory;
	private final Sampler sampler;
	private final ScheduledExecutorService executor;

	CloudTracer(final Builder builder) {
		this.resource = builder.resource.orElseGet(() -> ResourceType.autoDetect().monitoredResource());
		this.projectName = ProjectName.of(builder.projectId.orElseGet(() -> ResourceType.get(this.resource, PROJECT_ID).get()));
		this.client = builder.client.orElseGet(CloudTracer::defaultTraceServiceClient);
		this.sampler = builder.sampler.orElse(defaultSampler());
		this.executor = builder.executor.orElseGet(Executors::newSingleThreadScheduledExecutor);

		this.factory = new Factory(this.resource);
		this.executor.scheduleAtFixedRate(this::flush, 10, 10, TimeUnit.SECONDS);
	}

	// @Override
	// public Span getCurrentSpan() {
	// 	var scope = this.tlsScope.get();
	// 	return scope != null ? scope.span : null;
	// }

	// @Override
	// public Scope withSpan(Span span) {
	// 	return new CloudTraceScope(span);
	// }

	@Override
	public Span.Builder spanBuilder(String spanName) {
		return CloudTraceSpan.builder(spanName, this);
	}

	@Override
	public void close() {
		this.executor.shutdown();
		try {
			if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
				this.executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			this.executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
		flush();
		GaxUtil.close(this.client);
	}

	/**
	 *
	 */
	private void flush() {
		var drain = new LinkedList<CloudTraceSpan>();
		this.spans.drainTo(drain);
		if (drain.isEmpty()) {
			return;
		}
		var converted = drain.stream().map(factory::toSpan).collect(toList());
		LOG.log(Level.FINE, "Flushing spans... [size={0}]", converted.size());
		try {
			this.client.batchWriteSpans(projectName, converted);
		} catch (ApiException e) {
			LOG.log(Level.WARNING, "Unable to persist span!", e);
		}
	}

	/**
	 *
	 * @param span
	 */
	public void persist(final CloudTraceSpan span) {
		if (!span.getSpanContext().isSampled()) {
			return;
		}
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
	 * @return a new builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}


	// --- Inner Classes ---

	/**
	 *
	 */
	public static class Builder {
		private Optional<String> projectId = Optional.empty();
		private Optional<MonitoredResource> resource = Optional.empty();
		private Optional<TraceServiceClient> client = Optional.empty();

		private Optional<Sampler> sampler = Optional.empty();
		private Optional<ScheduledExecutorService> executor = Optional.empty();

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
		 * @param sampler the sampler to set
		 * @return
		 */
		public Builder sampler(Sampler sampler) {
			this.sampler = Optional.of(sampler);
			return this;
		}

		/**
		 * @param executor
		 * @return
		 */
		public Builder executor(ScheduledExecutorService executor) {
			this.executor = Optional.of(executor);
			return this;
		}

		/**
		 *
		 * @return
		 */
		public CloudTracer build() {
			return new CloudTracer(this);
		}
	}
}
