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
package uk.dansiviter.gcp.microprofile.config;

import static uk.dansiviter.gcp.AtomicInit.atomic;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.google.cloud.MetadataConfig;
import com.google.cloud.ServiceOptions;

import org.eclipse.microprofile.config.spi.ConfigSource;

class MetadataConfigSource implements ConfigSource {
	private static final Map<String, Supplier<String>> MAP = Map.of(
		"gcp.project-id", atomic(ServiceOptions::getDefaultProjectId),
		"gcp.cluster-name", MetadataConfig::getClusterName,
		"gcp.cluster-location", () -> MetadataConfig.getAttribute("instance/attributes/cluster-location"),
		"gcp.default-sa.email", () -> MetadataConfig.getAttribute("instance/service-accounts/default/email")
	);

	@Override
	public Map<String, String> getProperties() {
		return Map.of();
	}

	@Override
	public Set<String> getPropertyNames() {
		return Set.of();
	}

	@Override
	public String getValue(String propertyName) {
		var supplier = MAP.get(propertyName);
		return supplier != null ? supplier.get() : null;
	}

	@Override
	public String getName() {
		return "gcp-metadata-server";
	}
}
