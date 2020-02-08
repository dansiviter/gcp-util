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

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import uk.dansiviter.stackdriver.microprofile.metrics.jaxrs.ContainerMetricsFeature;

/**
 *
 */
@ApplicationScoped
@ApplicationPath("/")
public class TestApplication extends Application {
	@Override
	public Set<Class<?>> getClasses() {
		return Set.of(TestResource.class, ContainerMetricsFeature.class, ErrorHandler.class);
	}

	@Provider
	public static class ErrorHandler implements ExceptionMapper<Exception> {
		private static final Logger LOG = Logger.getLogger(ErrorHandler.class.getName());
		@Override
		public Response toResponse(Exception ex) {
			if (ex instanceof WebApplicationException) {
				Level level = Level.WARNING;
				WebApplicationException wex = (WebApplicationException) ex;
				if (wex.getResponse().getStatusInfo().getFamily() == Family.SERVER_ERROR) {
					level = Level.SEVERE;
				}
				LOG.log(level, "Exception on processing!", ex);
				return wex.getResponse();
			}

			LOG.log(Level.SEVERE, "Unexpected error! 500 will be returned.", ex);
			return Response.serverError().build();
		}
	}
}
