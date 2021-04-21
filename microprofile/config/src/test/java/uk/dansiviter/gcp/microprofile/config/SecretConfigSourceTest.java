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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.MonitoredResource;
import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.GetSecretVersionRequest;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient.ListSecretsPagedResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersion.State;
import com.google.cloud.secretmanager.v1.stub.SecretManagerServiceStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SecretConfigSource}.
 */
@ExtendWith(MockitoExtension.class)
class SecretConfigSourceTest {
	private final MonitoredResource resource = MonitoredResource.of("global", Map.of("project_id", "my_project"));

	@Mock
	private SecretManagerServiceSettings settings;
	@Mock
	private SecretManagerServiceStub stub;

	private SecretConfigSource source;

	@BeforeEach
	void before() {
		this.source = new SecretConfigSource(this.resource, () -> SecretManagerServiceClient.create(stub));
	}

	@Test
	void getName() {
		assertThat(this.source.getName(), equalTo("gcp-secrets"));
	}

	@Test
	void getOrdinal() {
		assertThat(this.source.getOrdinal(), equalTo(100));
	}

	@Test
	void getProperties() {
		assertThat(this.source.getProperties(), anEmptyMap());
	}

	@Test
	void getPropertyNames(
		@Mock UnaryCallable<ListSecretsRequest, ListSecretsPagedResponse> listCallable,
		@Mock ListSecretsPagedResponse response)
	{
		when(this.stub.listSecretsPagedCallable()).thenReturn(listCallable);
		when(listCallable.call(any())).thenReturn(response);
		when(response.iterateAll()).thenReturn(Set.of(Secret.newBuilder().setName("projects/acme/secrets/foo").build()));

		assertThat(this.source.getPropertyNames(), contains("secret:/foo"));
	}

	@Test
	void getValue(
		@Mock UnaryCallable<GetSecretVersionRequest, SecretVersion> versionCallable,
		@Mock UnaryCallable<AccessSecretVersionRequest, AccessSecretVersionResponse> valueCallable)
	throws InvalidProtocolBufferException
	{
		assertThat(this.source.getValue("foo"), nullValue());

		when(this.stub.getSecretVersionCallable()).thenReturn(versionCallable);
		var version = SecretVersion.newBuilder().setName("myVersion").setState(State.ENABLED).build();
		when(versionCallable.call(any())).thenReturn(version);
		when(this.stub.accessSecretVersionCallable()).thenReturn(valueCallable);
		when(valueCallable.call(any())).thenReturn(AccessSecretVersionResponse.newBuilder().setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("hello")).build()).build());

		assertThat(this.source.getValue("secrets/foo"), equalTo("hello"));
		assertThat(this.source.getValue("secrets/foo/versions/1"), equalTo("hello"));

		var request = ArgumentCaptor.forClass(GetSecretVersionRequest.class);
		verify(versionCallable, times(2)).call(request.capture());
		assertThat(request.getAllValues().get(0).getName(), equalTo("projects/my_project/secrets/foo/versions/latest"));
		assertThat(request.getAllValues().get(1).getName(), equalTo("projects/my_project/secrets/foo/versions/1"));
	}

	@Test
	void close() throws IOException {
		this.source.client();  // cause client creation

		this.source.close();

		verify(this.stub).close();
	}
}
