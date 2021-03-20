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
package uk.dansiviter.gcp.opentracing.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.OptionalLong;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentracing.propagation.TextMapAdapter;
import uk.dansiviter.gcp.opentracing.CloudTraceSpanContext;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [22 Dec 2019]
 */
@ExtendWith(MockitoExtension.class)
class B3MultiPropagatorTest {
	@InjectMocks
	private B3MultiPropagator propagator;

	@Test
	void inject() {
		var spanContext = CloudTraceSpanContext
				.builder(OptionalLong.of(0), 2748, 291)
				.sampled(true)
				.build();

		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var carrier = new TextMapAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("00000000000000000000000000000abc", map.get("x-b3-traceid"));
		assertEquals("0000000000000123", map.get("x-b3-spanid"));
		assertEquals("1", map.get("x-b3-sampled"));
	}

	@Test
	void inject_shortTraceId() {
		var spanContext = CloudTraceSpanContext
				.builder(OptionalLong.empty(), 2748, 291)
				.sampled(true)
				.build();

		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var carrier = new TextMapAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("0000000000000abc", map.get("x-b3-traceid"));
		assertEquals("0000000000000123", map.get("x-b3-spanid"));
		assertEquals("1", map.get("x-b3-sampled"));
	}

	@Test
	void inject_null() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var carrier = new TextMapAdapter(map);

		this.propagator.inject(null, carrier);

		assertTrue(map.isEmpty());
	}

	@Test
	void extract() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("x-b3-traceid", "00000000000000000000000000000abc");
		map.put("x-b3-spanid", "123");
		map.put("x-b3-sampled", "1");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("00000000000000000000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	void extract_shortTraceId() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("x-b3-traceid", "0000000000000abc");
		map.put("x-b3-spanid", "0000000000000123");
		map.put("x-b3-sampled", "1");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("0000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	void extract_poorFormat() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("x-b3-traceid", "abc");
		map.put("x-b3-spanid", "123");
		map.put("x-b3-sampled", "1");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("0000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	void extract_null() {
		var carrier = new TextMapAdapter(Collections.emptyMap());
		var actual = this.propagator.extract(carrier);

		assertNull(actual);
	}
}
