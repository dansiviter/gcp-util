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
package uk.dansiviter.stackdriver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Specializes;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.cloud.MonitoredResource;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.helidon.microprofile.server.Server;
import uk.dansiviter.stackdriver.log.jul.JulHandler;
import uk.dansiviter.stackdriver.log.opentracing.Decorator;
import uk.dansiviter.stackdriver.microprofile.metrics.MonitoredResourceProvider;

/**
 *
 */
public class KitchenSinkIT {
	private static final String TEST_ID = RandomStringUtils.randomAlphanumeric(6);
	static final MonitoredResource RESOURCE = resource();

	private Server server;

	@BeforeEach
	public void before() {
		System.out.println("Using test id: " + TEST_ID);
		this.server = Server.builder()
				.port(availablePort())
				.addApplication(TestApplication.class)
				.build();
        this.server.start();
	}

	@Test
	public void test() {
		final WebTarget target = target().path("test");

		final Invocation.Builder builder = target.request();
		try (Response res = builder.get()) {
			assertEquals(200, res.getStatusInfo().getStatusCode());
		}
	}

	@AfterEach
	public void after() {
		if (this.server != null) {
			this.server.stop();
		}
	}

	private WebTarget target() {
		final String baseUri = "http://localhost:" + server.port();
		System.out.println("Using: " + baseUri);
		return ClientBuilder.newClient().target(baseUri);
	}

	private static int availablePort() {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		} catch (IOException e) {
			throw new AssertionError("Unable to find port!", e);
		}
	}

	@ApplicationPath("/")
	@ApplicationScoped
	public static class TestApplication extends Application {
		@Override
		public Set<Class<?>> getClasses() {
			return Set.of(TestResource.class);
		}
	}

	@ApplicationScoped
	public static class MyResourceProvider extends MonitoredResourceProvider {
		@Override @javax.enterprise.inject.Produces @Specializes
		public MonitoredResource monitoredResource() {
			return RESOURCE;
		}
	}

	@ApplicationScoped
	public static class ExecutorProvider {
		@javax.enterprise.inject.Produces @ApplicationScoped
		public ScheduledExecutorService scheduler() {
			return Executors.newSingleThreadScheduledExecutor();
		}

		public static void dispose(@Disposes ScheduledExecutorService scheduler) {
			scheduler.shutdown();
		}
	}

	@Path("test")
	@RequestScoped
	public static class TestResource {
		private static final Logger LOG = Logger.getLogger(TestResource.class.getName());

		@GET
		@Produces(MediaType.TEXT_PLAIN)
		public String get() {
			LOG.info("hello!");
			return "hello";
		}
	}

	@BeforeAll
	public static void beforeClass() {
		final JulHandler handler = JulHandler.julHandler(RESOURCE);
		handler.setLevel(Level.FINEST);
		handler.add(new Decorator());

		final Logger root = Logger.getLogger("");
		root.setLevel(Level.INFO);
		root.addHandler(handler);
	}

	/**
	 *
	 * @return
	 */
	private static MonitoredResource resource() {
		// annoyingly, metrics require complete set but trace and logging don't!
		return ResourceType.GCE_INSTANCE.monitoredResource(k -> {
			switch (k) {
				case "instance_id":
				return Optional.of(TEST_ID);
				case "zone":
				return Optional.of("europe-west2");
			default:
				return Optional.empty();
			}
		});
	}
}
