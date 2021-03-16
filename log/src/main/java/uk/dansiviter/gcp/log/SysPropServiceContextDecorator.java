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

import static java.lang.System.getProperty;

import java.util.Map;

import com.google.cloud.logging.LogEntry.Builder;

/**
 * A {@link EntryDecorator} that uses {@code serviceContext.service} and {@code serviceContext.version} System
 * Properties to decorate the log messages for {@code serviceContext}.
 *
 * @author Daniel Siviter
 * @since v1.0 [16 Jan 2020]
 */
public class SysPropServiceContextDecorator implements EntryDecorator {
	private final EntryDecorator delegate;

	/**
	 * Creates a new instance.
	 */
	public SysPropServiceContextDecorator() {
		this.delegate = EntryDecorator.serviceContext(
			getProperty("serviceContext.service"),
			getProperty("serviceContext.version"));
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		this.delegate.decorate(b, e, payload);
	}
}
