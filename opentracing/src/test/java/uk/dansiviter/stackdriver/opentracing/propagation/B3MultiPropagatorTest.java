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

import java.util.Collections;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapAdapter;
import uk.dansiviter.stackdriver.opentracing.StackdriverSpanContext;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [22 Dec 2019]
 */
@ExtendWith(MockitoExtension.class)
public class B3MultiPropagatorTest {
	@InjectMocks
	private B3MultiPropagator propagator;

	@Test
	public void inject() {
		StackdriverSpanContext spanContext = StackdriverSpanContext
				.builder(OptionalLong.of(0), 2748, 291)
				.sampled(true)
				.build();

		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		TextMap carrier = new TextMapAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("00000000000000000000000000000abc", map.get("x-b3-traceid"));
		assertEquals("0000000000000123", map.get("x-b3-spanid"));
		assertEquals("1", map.get("x-b3-sampled"));
	}

	@Test
	public void inject_shortTraceId() {
		StackdriverSpanContext spanContext = StackdriverSpanContext
				.builder(OptionalLong.empty(), 2748, 291)
				.sampled(true)
				.build();

		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		TextMap carrier = new TextMapAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("0000000000000abc", map.get("x-b3-traceid"));
		assertEquals("0000000000000123", map.get("x-b3-spanid"));
		assertEquals("1", map.get("x-b3-sampled"));
	}

	@Test
	public void inject_null() {
		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		TextMap carrier = new TextMapAdapter(map);

		this.propagator.inject(null, carrier);

		assertTrue(map.isEmpty());
	}

	@Test
	public void extract() {
		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		map.put("x-b3-traceid", "00000000000000000000000000000abc");
		map.put("x-b3-spanid", "123");
		map.put("x-b3-sampled", "1");

		TextMap carrier = new TextMapAdapter(map);
		StackdriverSpanContext actual = this.propagator.extract(carrier);

		assertEquals("00000000000000000000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_shortTraceId() {
		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		map.put("x-b3-traceid", "0000000000000abc");
		map.put("x-b3-spanid", "0000000000000123");
		map.put("x-b3-sampled", "1");

		TextMap carrier = new TextMapAdapter(map);
		StackdriverSpanContext actual = this.propagator.extract(carrier);

		assertEquals("0000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_poorFormat() {
		Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		map.put("x-b3-traceid", "abc");
		map.put("x-b3-spanid", "123");
		map.put("x-b3-sampled", "1");

		TextMap carrier = new TextMapAdapter(map);
		StackdriverSpanContext actual = this.propagator.extract(carrier);

		assertEquals("0000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_null() {
		TextMap carrier = new TextMapAdapter(Collections.emptyMap());
		StackdriverSpanContext actual = this.propagator.extract(carrier);

		assertNull(actual);
	}
}
