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
package uk.dansiviter.gcp.microprofile.metrics;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

import uk.dansiviter.gcp.Util;

/**
 * Override with the {@link jakarta.enterprise.inject.Specializes} mechanism if you need to override.
 */
public class SchedulerProducer {
	@Produces
	@ApplicationScoped
	public ScheduledExecutorService scheduler() {
		return Executors.newSingleThreadScheduledExecutor();
	}

	public void shutdown(@Disposes ScheduledExecutorService scheduler) {
		Util.shutdown(scheduler, 60, SECONDS);
	}
}
