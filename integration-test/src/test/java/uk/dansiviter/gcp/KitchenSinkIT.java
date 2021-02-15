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

import static com.google.cloud.logging.Logging.EntryListOption.filter;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.helidon.microprofile.server.Server;

/**
 *
 */
public class KitchenSinkIT {
	private static final String TEST_ID = RandomStringUtils.randomAlphanumeric(6).toUpperCase();

	private static Server SERVER;
	private static URI BASE_URI;

	@BeforeAll
	public static void beforeClass() throws URISyntaxException {
		File file = new File(KitchenSinkIT.class.getResource("/logging.properties").toURI());
		System.setProperty("java.util.logging.config.file", file.getAbsolutePath());
		System.out.println("Using test id: " + TEST_ID);

		SERVER = Server.builder().port(availablePort()).addApplication(TestApplication.class).build();
		SERVER.start();
		BASE_URI = URI.create("http://localhost:" + SERVER.port());
		System.out.println("Using: " + BASE_URI);
	}

	@Test
	public void get() {
		final WebTarget target = target().path("test");

		final Invocation.Builder builder = target.request();
		try (Response res = builder.get()) {
			assertEquals(200, res.getStatusInfo().getStatusCode());
		}
	}

	@Test
	public void clientError() {
		final WebTarget target = target().path("test/client-error");

		final Invocation.Builder builder = target.request();
		try (Response res = builder.get()) {
			assertEquals(400, res.getStatusInfo().getStatusCode());
		}
	}

	@Test
	public void serverError() {
		final WebTarget target = target().path("test/server-error");

		final Invocation.Builder builder = target.request();
		try (Response res = builder.get()) {
			assertEquals(500, res.getStatusInfo().getStatusCode());
		}

		List<LogEntry> logs = await().atLeast(1, TimeUnit.MINUTES).until(
			() -> getLogs("hello!"),
			l -> !l.isEmpty());
		System.out.println(logs);
	}

	@AfterAll
	public static void after() throws InterruptedException {
		if (SERVER != null) {
			SERVER.stop();
		}
	}

	private WebTarget target() {
		return ClientBuilder.newClient().target(BASE_URI);
	}

	private static int availablePort() {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		} catch (IOException e) {
			throw new AssertionError("Unable to find port!", e);
		}
	}

	public List<LogEntry> getLogs(String msg) {
		Logging logging = LoggingOptions.getDefaultInstance().getService();
		String filter = "resource.labels.instance_id=\"" + TEST_ID + "\""; //" AND jsonPayload.message=\"" + msg + '"';
		return StreamSupport
				.stream(logging.listLogEntries(filter(filter))
						.iterateAll()
						.spliterator(), false)
				.collect(Collectors.toList());
	}
}
