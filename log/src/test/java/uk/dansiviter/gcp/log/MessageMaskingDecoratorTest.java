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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import com.google.cloud.logging.LogEntry.Builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link MessageMaskingDecorator}
 */
@ExtendWith(MockitoExtension.class)
class MessageMaskingDecoratorTest {
	@Test
	void decorate(@Mock Builder b, @Mock Entry e) {
		var decorator = new MessageMaskingDecorator("foo");

		var payload = new HashMap<String, Object>();
		payload.put("message", "hello");
		decorator.decorate(b, e, payload);
		assertEquals("hello", payload.get("message"));

		payload.put("message", "hello foo");
		decorator.decorate(b, e, payload);
		assertEquals("hello **REDACTED**", payload.get("message"));

		payload.put("message", "foo hello");
		decorator.decorate(b, e, payload);
		assertEquals("**REDACTED** hello", payload.get("message"));
	}
}
