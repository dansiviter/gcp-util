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

import static java.util.Collections.emptyMap;

import java.util.Map;

import javax.annotation.Nonnull;

import com.google.cloud.logging.LogEntry.Builder;

/**
 * Adds {@code serviceContext} into the payload.
 */
public class ServiceContextDecorator implements EntryDecorator {
	private final Map<String, String> serviceContext;

	/**
	 * @param pkg the package to extract `Implementation-Title` and `Implementation-Version` from.
	 */
	@SuppressWarnings("deprecation")  // ClassLoader#getDefinedPackage not supported by GraalVM yet
	public ServiceContextDecorator(@Nonnull String pkg) {
		this(Package.getPackage(pkg));
	}

	/**
	 * @param cls the class to get the {@link Package} from.
	 * @see #ServiceContextDecorator(Package)
	 */
	public ServiceContextDecorator(@Nonnull Class<?> cls) {
		this(cls.getPackage());
	}

	/**
	 * @param pkg the package to extract `Implementation-Title` and `Implementation-Version` from.
	 */
	public ServiceContextDecorator(@Nonnull Package pkg) {
		this(pkg.getImplementationTitle(), pkg.getImplementationVersion());
	}

	/**
	 * @param service the service name.
	 * @param version the service version.
	 */
	public ServiceContextDecorator(String service, String version) {
		this.serviceContext = service != null ? Map.of("service", service, "version", version) : emptyMap();
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		if (this.serviceContext.isEmpty()) {
			return;
		}
		payload.put("serviceContext", this.serviceContext);
	}
}
