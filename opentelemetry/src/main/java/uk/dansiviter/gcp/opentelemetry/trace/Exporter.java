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
package uk.dansiviter.gcp.opentelemetry.trace;

import static uk.dansiviter.gcp.ResourceType.Label.PROJECT_ID;

import java.io.IOException;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.cloud.MonitoredResource;
import com.google.cloud.trace.v2.TraceServiceClient;
import com.google.devtools.cloudtrace.v2.ProjectName;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import uk.dansiviter.gcp.GaxUtil;
import uk.dansiviter.gcp.MonitoredResourceProvider;
import uk.dansiviter.jule.LogProducer;

/**
 * A OpenTelemetry {@link SpanExporter exporter} that pushes the traces to Cloud Trace.
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Feb 2021]
 */
public class Exporter implements SpanExporter {
	private static final Logger LOG = LogProducer.log(Logger.class);

	private final MonitoredResource resource;
	private final ProjectName projectName;
	private final TraceServiceClient client;
	private final Factory factory;

	Exporter(Builder builder) {
		this.resource = builder.resource.orElseGet(MonitoredResourceProvider::monitoredResource);
		var projectId = builder.projectId.or(() -> PROJECT_ID.get(this.resource));
		this.projectName = ProjectName.of(projectId.orElseThrow());
		this.client = builder.client.orElseGet(Exporter::defaultTraceServiceClient);
		this.factory = new Factory(this.resource, this.projectName);
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		LOG.export(spans::size);
		this.client.batchWriteSpans(projectName, factory.toSpans(spans));
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode flush() {
		// client doesn't have a flush method
		return CompletableResultCode.ofFailure();
	}

	@Override
	public CompletableResultCode shutdown() {
		LOG.shutdown();
		GaxUtil.close(this.client);
		return CompletableResultCode.ofSuccess();
	}


	// --- Static Methods ---

	/**
	 * @return a new builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * @return create exporter with default configuration.
	 */
	public static Exporter createDefault() {
		return builder().build();
	}

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

		// --- Inner Classes ---

	/**
	 *
	 */
	public static class Builder {
		private Optional<String> projectId = Optional.empty();
		private Optional<MonitoredResource> resource = Optional.empty();
		private Optional<TraceServiceClient> client = Optional.empty();

		private Builder() { }

		/**
		 * @param client the client to set
		 * @return this builder instance.
		 */
		public Builder client(TraceServiceClient client) {
			this.client = Optional.of(client);
			return this;
		}

		/**
		 * @param projectId the projectId to set
		 * @return this builder instance.
		 */
		public Builder projectId(String projectId) {
			this.projectId = Optional.of(projectId);
			return this;
		}

		/**
		 * @param resource the resource to set
		 * @return this builder instance.
		 */
		public Builder resource(MonitoredResource resource) {
			this.resource = Optional.of(resource);
			return this;
		}

		/**
		 * @return build new exporter.
		 * @throws NoSuchElementException if a required value is missing.
		 */
		public Exporter build() {
			return new Exporter(this);
		}
	}
}
