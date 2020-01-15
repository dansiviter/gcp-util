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
package uk.dansiviter.stackdriver.opentracing.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import uk.dansiviter.stackdriver.opentracing.StackdriverSpanContext;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [22 Dec 2019]
 */
@ExtendWith(MockitoExtension.class)
public class B3SinglePropagatorTest {
	@InjectMocks
	private B3SinglePropagator propagator;

	@Test
	public void inject(@Mock StackdriverSpanContext spanContext) {
		when(spanContext.traceId()).thenReturn("abc");
		when(spanContext.spanIdAsString()).thenReturn("123");
		when(spanContext.sampled()).thenReturn(true);

		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		TextMap carrier = new TextMapInjectAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("abc-123-1", map.get("b3"));
	}

	@Test
	public void inject_null() {
		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		TextMap carrier = new TextMapInjectAdapter(map);

		this.propagator.inject(null, carrier);

		assertTrue(map.isEmpty());
	}

	@Test
	public void extract() {
		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		map.put("b3", "123-abc-1");
		TextMap carrier = new TextMapExtractAdapter(map);
		StackdriverSpanContext actual = this.propagator.extract(carrier);

		assertEquals("0000000000000123", actual.traceId());
		assertEquals("abc", actual.spanIdAsString());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_null() {
		TextMap carrier = new TextMapExtractAdapter(Collections.emptyMap());
		StackdriverSpanContext actual = this.propagator.extract(carrier);

		assertNull(actual);
	}
}
