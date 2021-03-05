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

import static java.util.stream.Collectors.toList;
import static uk.dansiviter.gcp.ResourceType.Label.PROJECT_ID;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.google.cloud.MonitoredResource;
import com.google.cloud.trace.v2.TraceServiceClient;
import com.google.devtools.cloudtrace.v2.ProjectName;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import uk.dansiviter.gcp.ResourceType;

/**
 * A OpenTelemetry {@link SpanExporter exporter} that pushes the traces to Cloud Trace.
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Feb 2021]
 */
public class Exporter implements SpanExporter {
    private final MonitoredResource resource;
	private final ProjectName projectName;
	private final TraceServiceClient client;
	private final Factory factory;

	Exporter(final Builder builder) {
		this.resource = builder.resource.orElseGet(() -> ResourceType.monitoredResource());
		this.projectName = ProjectName.of(builder.projectId.orElseGet(() -> ResourceType.get(this.resource, PROJECT_ID).get()));
		this.client = builder.client.orElseGet(Exporter::defaultTraceServiceClient);
        this.factory = new Factory(this.resource, this.projectName);
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        this.client.batchWriteSpans(projectName, spans.stream().map(factory::toSpan).collect(toList()));
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        // client doesn't have a flush method
        return CompletableResultCode.ofFailure();
    }

    @Override
    public CompletableResultCode shutdown() {
        this.client.shutdown();
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
		 */
		public Builder client(@Nonnull TraceServiceClient client) {
			this.client = Optional.of(client);
			return this;
		}

		/**
		 * @param projectId the projectId to set
		 */
		public Builder projectId(@Nonnull String projectId) {
			this.projectId = Optional.of(projectId);
			return this;
		}

		/**
		 * @param resource the resource to set
		 */
		public Builder resource(@Nonnull MonitoredResource resource) {
			this.resource = Optional.of(resource);
			return this;
		}

		/**
		 *
		 * @return
		 */
		public Exporter build() {
			return new Exporter(this);
		}
	}
}
