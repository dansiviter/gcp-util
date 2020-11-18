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
package uk.dansiviter.gcp.monitoring;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;

/**
 * @author Daniel Siviter
 * @since v1.0 [3 Feb 2020]
 */
@ApplicationScoped
public class ExecutorProvider {
	@Produces
	@ApplicationScoped
	public static ScheduledExecutorService scheduler() {
		return Executors.newSingleThreadScheduledExecutor();
	}

	public static void dispose(@Disposes ScheduledExecutorService scheduler) {
		scheduler.shutdown();
	}
}
