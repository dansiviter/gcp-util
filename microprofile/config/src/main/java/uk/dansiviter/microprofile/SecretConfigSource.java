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
package uk.dansiviter.microprofile;

import static java.util.Collections.emptyMap;
import static uk.dansiviter.stackdriver.ResourceType.Label.PROJECT_ID;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import com.google.api.pathtemplate.PathTemplate;
import com.google.cloud.MonitoredResource;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersion.State;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import org.eclipse.microprofile.config.spi.ConfigSource;

import uk.dansiviter.stackdriver.ResourceType;

/**
 * Supports 3 formats of secret name:
 * <ul>
 *  <li><code>secrets/{secret}</code> - This will default to current projectId and 'latest' version,</li>
 *  <li><code>secrets/{secret}/versions/{version}</code> - This will default to current projectId,</li>
 *  <li><code>projects/{project}/secrets/{secret}/versions/{version}</code> - Standard GCP naming convention.</li>
 * </ul>
 *
 * @author Daniel Siviter
 * @since v1.0 [28 Apr 2020]
 */
public class SecretConfigSource implements ConfigSource, Closeable {
	private static final PathTemplate SECRET_TEMPLATE = PathTemplate.createWithoutUrlEncoding("secrets/{secret}");
	private static final PathTemplate SECRET_VERSION_TEMPLATE = PathTemplate.createWithoutUrlEncoding("secrets/{secret}/versions/{version}");

	private final AtomicReference<SecretManagerServiceClient> client = new AtomicReference<>();

	private final String projectId;
	private final SecretManagerServiceSettings settings;

	public SecretConfigSource() throws IOException {
		this(ResourceType.autoDetect().monitoredResource(), SecretManagerServiceSettings.newBuilder().build());
	}

	public SecretConfigSource(MonitoredResource resource, SecretManagerServiceSettings settings) {
		this.projectId = PROJECT_ID.get(resource).get();
		this.settings = settings;
	}

	@Override
	public Map<String, String> getProperties() {
		return emptyMap(); // avoid to prevent accidental logging of values
	}

	@Override
	public Set<String> getPropertyNames() {
		Set<String> names = new HashSet<>();
		for (Secret s : client().listSecrets(this.projectId).iterateAll()) {
			String name = s.getName();
			names.add("secret:".concat(name.substring(name.lastIndexOf('/'))));
		}
		return names;
	}

	@Override
	public String getValue(String propertyName) {
		SecretVersionName name = versionName(propertyName);
		if (name == null) {
			return null;
		}
		SecretVersion version = client().getSecretVersion(name);
		if (version.getState() == State.ENABLED) { // not sure this is required
			AccessSecretVersionResponse response = client().accessSecretVersion(name);
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
		SecretManagerServiceClient c = this.client.getAcquire();
		if (c != null) {
			c.close();
		}
	}

	private SecretManagerServiceClient client() {
		return this.client.updateAndGet(c -> {
			try {
				return c != null ? c : SecretManagerServiceClient.create(settings);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private SecretVersionName versionName(@Nonnull String in) {
		Map<String, String> values = SECRET_TEMPLATE.match(in);
		if (values != null) {
			return SecretVersionName.of(this.projectId, values.get("secret"), "latest");
		}
		values = SECRET_VERSION_TEMPLATE.match(in);
		if (values != null) {
			return SecretVersionName.of(this.projectId, values.get("secret"), values.get("version"));
		}
		if (SecretVersionName.isParsableFrom(in)) {
			return SecretVersionName.parse(in);
		}
		return null;
	}
}
