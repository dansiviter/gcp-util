/*
 * Copyright 2019 Daniel Siviter
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
package uk.dansiviter.stackdriver;

import io.helidon.config.Config;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.spi.TracerProvider;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import uk.dansiviter.stackdriver.opentracing.StackdriverTracer;
import uk.dansiviter.stackdriver.opentracing.sampling.Sampler;

public class StackdriverTracerProvider implements TracerProvider {

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
			final Tracer tracer = StackdriverTracer.builder()
					.resource(KitchenSinkIT.RESOURCE)
					.sampler(Sampler.always()).build();
			GlobalTracer.register(tracer);
			return tracer;
		}
	}
}
