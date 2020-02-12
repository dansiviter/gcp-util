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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import uk.dansiviter.stackdriver.api.Post;

/**
 * @author Daniel Siviter
 * @since v1.0 [3 Feb 2020]
 */
@Path("test")
@RequestScoped
public class TestResource {
	private static final Logger LOG = Logger.getLogger(TestResource.class.getName());

	@Inject
    @RestClient
    private JsonPlaceholderService service;

	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String get() throws InterruptedException {
		Thread.sleep((long) (Math.random() * 250));
		LOG.info("hello!");
		return "hello";
	}

	@GET
	@Path("client-error")
	public String clientError() {
		throw new BadRequestException("Oooops!");
	}

	@GET
	@Path("server-error")
	public String serverError() {
		LOG.severe("Nightmare!");
		LOG.log(Level.SEVERE, "Geeze!", new IllegalStateException("Doh!"));
		throw new IllegalStateException("Oh no!");
	}

	@GET
	@Path("downstream")
	public void downstream(@Suspended AsyncResponse res) {
		LOG.info("Getting posts from downstream!");
		this.service.posts().whenComplete((r, t) -> complete(res, r, t));
	}

	private void complete(AsyncResponse res, List<Post> posts, Throwable t) {
		LOG.info("Got posts.");
		if (t != null) {
			res.resume(t);
			return;
		}
		res.resume(posts);
	}
}
