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
package uk.dansiviter.gcp.monitoring;

import io.helidon.config.Config;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import uk.dansiviter.gcp.opentracing.Sampler;
import uk.dansiviter.gcp.opentracing.CloudTracer;

/**
 * @author Daniel Siviter
 * @since v1.0 [3 Feb 2020]
 */
public class CloudTracerProvider implements TracerProvider {

	@Override
	public TracerBuilder<?> createBuilder() {
		return new Builder();
	}

	private static class Builder implements TracerBuilder<Builder> {

		@Override
		public Builder serviceName(String name) {
			return this;
		}

		@Override
		public Builder collectorProtocol(String protocol) {
			return this;
		}

		@Override
		public Builder collectorPort(int port) {
			return this;
		}

		@Override
		public Builder collectorHost(String host) {
			return this;
		}

		@Override
		public Builder collectorPath(String path) {
			return this;
		}

		@Override
		public Builder addTracerTag(String key, String value) {
			return this;
		}

		@Override
		public Builder addTracerTag(String key, Number value) {
			return this;
		}

		@Override
		public Builder addTracerTag(String key, boolean value) {
			return this;
		}

		@Override
		public Builder config(Config config) {
			return this;
		}

		@Override
		public Builder enabled(boolean enabled) {
			return this;
		}

		@Override
		public Builder registerGlobal(boolean global) {
			return this;
		}

		@Override
		public Tracer build() {
			if (GlobalTracer.isRegistered()) {
				return GlobalTracer.get();
			}
			var tracer = CloudTracer
					.builder()
					.sampler(Sampler.alwaysSample())
					// .scopeManager(new HelidonScopeManager())
					.build();
			GlobalTracer.registerIfAbsent(tracer);
			return tracer;
		}
	}
}
