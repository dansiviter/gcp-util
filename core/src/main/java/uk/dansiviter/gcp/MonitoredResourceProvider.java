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
package uk.dansiviter.gcp;

import java.util.ServiceLoader;

import com.google.cloud.MonitoredResource;

/**
 * Provides the {@link MonitoredResource} via {@link ServiceLoader}.
 */
public interface MonitoredResourceProvider {
	static AtomicInit<MonitoredResource> MONITORED_RESOURCE = new AtomicInit<>(MonitoredResourceProvider::load);

	/**
	 * @return the monitored resource.
	 */
	MonitoredResource get();

	/**
	 * @return the found resource or {@link ResourceType#monitoredResource()}
	 */
	private static MonitoredResource load() {
		var resource = ServiceLoader.load(MonitoredResource.class)
			.findFirst()
			.orElseGet(ResourceType::monitoredResource);

		System.out.printf("### Using %s ###%n", resource);
		return resource;
	}

	/**
	 * @return the found resource or {@link ResourceType#monitoredResource()}
	 */
	static MonitoredResource monitoredResource() {
		return MONITORED_RESOURCE.get();
	}
}
