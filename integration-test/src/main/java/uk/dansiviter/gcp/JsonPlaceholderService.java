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

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import uk.dansiviter.gcp.api.Post;

/**
 * Simple Rest Client to demonstrate functionality.
 *
 * @author Daniel Siviter
 * @since v1.0 [11 Feb 2020]
 */
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(baseUri="https://jsonplaceholder.typicode.com")
public interface JsonPlaceholderService {
	@GET
	@Path("posts")
	CompletionStage<List<Post>> posts();
}
