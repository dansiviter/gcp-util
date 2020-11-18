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

import java.util.concurrent.TimeUnit;

import com.google.api.gax.core.BackgroundResource;

/**
 * @author Daniel Siviter
 * @since v1.0 [26 Jan 2020]
 */
public enum GaxUtil{ ;

	/**
	 *
	 * @param resource
	 */
	public static void close(BackgroundResource resource) {
		if (resource == null) {
			return;
		}
		try {
			resource.shutdown();
			if (!resource.awaitTermination(5, TimeUnit.SECONDS)) {
				resource.shutdownNow();
			}
		} catch (InterruptedException e) {
			resource.shutdownNow();
			Thread.currentThread().interrupt();
		}
		try {
			resource.close();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
