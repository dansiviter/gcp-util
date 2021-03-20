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
package uk.dansiviter.gcp.log;

import java.util.Map;
import java.util.ServiceLoader;

import com.google.cloud.logging.LogEntry.Builder;

/**
 * A {@link EntryDecorator} that uses {@link ServiceLoader} to extract another {@link EntryDecorator}s.
 *
 * @author Daniel Siviter
 * @since v1.0 [16 Jan 2020]
 */
public class ServiceLoaderDecorator implements EntryDecorator {
	private final ServiceLoader<EntryDecorator> decorators;

	/**
	 * Creates a new instance.
	 */
	public ServiceLoaderDecorator() {
		this.decorators = ServiceLoader.load(EntryDecorator.class);
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		this.decorators.forEach(d -> d.decorate(b, e, payload));
	}
}
