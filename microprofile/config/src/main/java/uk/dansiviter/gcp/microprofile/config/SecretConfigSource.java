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

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.google.api.pathtemplate.PathTemplate;
import com.google.cloud.MonitoredResource;
import com.google.cloud.ServiceOptions;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretVersion.State;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import org.eclipse.microprofile.config.spi.ConfigSource;

import uk.dansiviter.gcp.AtomicInit;

/**
 * Supports 3 formats of secret name:
 * <ul>
 * <li><code>secrets/{secret}</code> - This will default to current projectId
 * and 'latest' version,</li>
 * <li><code>secrets/{secret}/versions/{version}</code> - This will default to
 * current projectId,</li>
 * <li><code>projects/{project}/secrets/{secret}/versions/{version}</code> -
 * Standard GCP naming convention.</li>
 * </ul>
 *
 * @author Daniel Siviter
 * @since v1.0 [28 Apr 2020]
 */
public class SecretConfigSource implements ConfigSource, Closeable {
	private static final PathTemplate SECRET_TEMPLATE = PathTemplate.createWithoutUrlEncoding("secrets/{secret}");
	private static final PathTemplate SECRET_VERSION_TEMPLATE = PathTemplate
			.createWithoutUrlEncoding("secrets/{secret}/versions/{version}");

	private final AtomicInit<SecretManagerServiceClient> client;

	private final Optional<String> projectId;

	/**
	 * Creates a new instance with auto-detected {@link MonitoredResource} and default
	 * {@link SecretManagerServiceSettings}.
	 */
	SecretConfigSource() {
		this(Optional.of(ServiceOptions.getDefaultProjectId()), SecretConfigSource::createClient);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param projectId the project identifier.
	 * @param settings the settings.
	 */
	SecretConfigSource(
		Optional<String> projectId,
		@Nonnull Supplier<SecretManagerServiceClient> clientSupplier)
	{
		this.projectId = projectId;
		this.client = new AtomicInit<>(clientSupplier);
	}

	private static SecretManagerServiceClient createClient() {
		try {
			return SecretManagerServiceClient.create();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Map<String, String> getProperties() {
		return emptyMap();  // avoid to prevent accidental logging of values
	}

	@Override
	public Set<String> getPropertyNames() {
		return this.projectId.map(this::propertyNames).orElse(emptySet());
	}

	private Set<String> propertyNames(@Nonnull String projectId) {
		var names = new HashSet<String>();
		for (var s : client().listSecrets(projectId).iterateAll()) {
			var name = s.getName();
			names.add("secret:".concat(name.substring(name.lastIndexOf('/'))));
		}
		return names;
	}

	@Override
	public String getValue(String propertyName) {
		return this.projectId
			.map(p -> versionName(p, propertyName))
			.map(this::value)
			.orElse(null);
	}

	private String value(@Nonnull SecretVersionName name) {
		var version = client().getSecretVersion(name);
		if (version.getState() == State.ENABLED) {
			var response = client().accessSecretVersion(name);
			return response.getPayload().getData().toStringUtf8();
		}
		return null;
	}

	@Override
	public String getName() {
		return "gcp-secrets";
	}

	@Override
	public void close() throws IOException {
		try {
			this.client.close();
		} catch (Exception e) {
			throw e instanceof IOException ? (IOException) e : new IOException(e);
		}
	}

	SecretManagerServiceClient client() {
		return this.client.get();
	}

	private static SecretVersionName versionName(@Nonnull String projectId, @Nonnull String in) {
		var values = SECRET_TEMPLATE.match(in);
		if (values != null) {
			return SecretVersionName.of(projectId, values.get("secret"), "latest");
		}
		values = SECRET_VERSION_TEMPLATE.match(in);
		if (values != null) {
			return SecretVersionName.of(projectId, values.get("secret"), values.get("version"));
		}
		if (SecretVersionName.isParsableFrom(in)) {
			return SecretVersionName.parse(in);
		}
		return null;
	}
}
