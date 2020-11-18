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
package uk.dansiviter.gcp.monitoring.opentracing.propagation;

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
import uk.dansiviter.gcp.monitoring.opentracing.CloudTraceSpanContext;

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
	public void inject() {
		var spanContext = CloudTraceSpanContext
				.builder(OptionalLong.of(0), 2748, 291)
				.sampled(true)
				.build();

		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var carrier = new TextMapAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("00000000000000000000000000000abc-0000000000000123-1", map.get("b3"));
	}

	@Test
	public void inject_shortTraceId() {
		var spanContext = CloudTraceSpanContext
				.builder(OptionalLong.empty(), 2748, 291)
				.sampled(true)
				.build();

		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var carrier = new TextMapAdapter(map);

		this.propagator.inject(spanContext, carrier);

		assertEquals("0000000000000abc-0000000000000123-1", map.get("b3"));
	}

	@Test
	public void inject_null() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var carrier = new TextMapAdapter(map);

		this.propagator.inject(null, carrier);

		assertTrue(map.isEmpty());
	}

	@Test
	public void extract() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("b3", "00000000000000000000000000000abc-0000000000000123-1");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("00000000000000000000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_shortTraceId() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("b3", "0000000000000abc-0000000000000123-1");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("0000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_poorFormat() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("b3", "abc-123-1");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("0000000000000abc", actual.toTraceId());
		assertEquals("0000000000000123", actual.toSpanId());
		assertEquals(true, actual.sampled());
	}

	@Test
	public void extract_null() {
		var carrier = new TextMapAdapter(Collections.emptyMap());
		var actual = this.propagator.extract(carrier);

		assertNull(actual);
	}

	@Test
	public void extract_deny() {
		var map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		map.put("b3", "0");

		var carrier = new TextMapAdapter(map);
		var actual = this.propagator.extract(carrier);

		assertEquals("0000000000000000", actual.toTraceId());
		assertEquals("0000000000000000", actual.toSpanId());
		assertEquals(false, actual.sampled());
	}
}
