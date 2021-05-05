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
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.logging.LogEntry.Builder;
import com.google.cloud.logging.LoggingEnhancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link EntryDecorator}.
 */
@ExtendWith(MockitoExtension.class)
class EntryDecoratorTest {
	@Test
	void mdc(@Mock Builder b, @Mock Entry e) {
		var decorator = EntryDecorator.mdc(() -> Map.<String, Object>of("Acme", "foo"));
		var payload = new HashMap<String, Object>();
		decorator.decorate(b, e, payload);

		@SuppressWarnings("unchecked")
		var mdc = (Map<String, Object>) payload.get("mdc");
		assertThat(mdc, hasEntry("Acme", "foo"));
	}

	@Test
	void mdc_empty(@Mock Builder b, @Mock Entry e) {
		var decorator = EntryDecorator.mdc(() -> Map.<String, Object>of());
		var payload = new HashMap<String, Object>();
		decorator.decorate(b, e, payload);

		@SuppressWarnings("unchecked")
		var mdc = (Map<String, Object>) payload.get("mdc");
		assertThat(mdc, nullValue());
	}

	@Test
	void decorator(@Mock LoggingEnhancer enhancer, @Mock Builder b, @Mock Entry e) {
		var decorator = EntryDecorator.decorator(enhancer);
		var payload = new HashMap<String, Object>();
		decorator.decorate(b, e, payload);

		verify(enhancer).enhanceLogEntry(b);
	}

	@Test
	void mdc(@Mock Builder b, @Mock Entry e, @Mock EntryDecorator decorator0, @Mock EntryDecorator decorator1) {
		var decorator = EntryDecorator.all(decorator0, decorator1);
		var payload = Map.<String, Object>of();
		decorator.decorate(b, e, payload);

		verify(decorator0).decorate(b, e, payload);
		verify(decorator1).decorate(b, e, payload);
	}
}
