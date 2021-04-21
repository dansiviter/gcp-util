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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.logging.LogEntry.Builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link ServiceContextDecorator}.
 */
@ExtendWith(MockitoExtension.class)
class ServiceContextDecoratorTest {
	@Test
	void decorate(@Mock Builder b, @Mock Entry e) {
		var decorator = new ServiceContextDecorator("Acme", "foo");
		var payload = new HashMap<String, Object>();
		decorator.decorate(b, e, payload);

		@SuppressWarnings("unchecked")
		var serviceContext = (Map<String, Object>) payload.get("serviceContext");
		assertThat(serviceContext, hasEntry("service", "Acme"));
		assertThat(serviceContext, hasEntry("version", "foo"));
	}

	@Test
	void decorate_class(@Mock Builder b, @Mock Entry e) {
		var decorator = new ServiceContextDecorator(getClass());
		var payload = new HashMap<String, Object>();
		decorator.decorate(b, e, payload);

		assertThat(payload.get("serviceContext"), nullValue());
	}

	@Test
	void decorate_pkgString(@Mock Builder b, @Mock Entry e) {
		var decorator = new ServiceContextDecorator(getClass().getPackage().getName());
		var payload = new HashMap<String, Object>();
		decorator.decorate(b, e, payload);

		assertThat(payload.get("serviceContext"), nullValue());
	}
}
