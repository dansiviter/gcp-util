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
package uk.dansiviter.gcp.log.log4j2;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.logging.LogEntry.Builder;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.gcp.log.Entry;

/**
 * Tests for {@link ThreadContextDecorator}.
 */
@ExtendWith(MockitoExtension.class)
class ThreadContextDecoratorTest {
	@Test
	void decorate(@Mock Builder b, @Mock Entry e) {
		var payload = new HashMap<String, Object>();
		ThreadContext.put("Acme", "foo");
		new ThreadContextDecorator().decorate(b, e, payload);

		@SuppressWarnings("unchecked")
		var mdc = (Map<String, Object>) payload.get("mdc");
		assertThat(mdc, hasEntry("Acme", "foo"));
	}
}
