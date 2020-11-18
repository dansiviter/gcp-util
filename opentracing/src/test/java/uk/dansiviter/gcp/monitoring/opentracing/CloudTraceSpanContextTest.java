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
package uk.dansiviter.gcp.monitoring.opentracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [15 Dec 2019]
 */
public class CloudTraceSpanContextTest {
	@Test
	public void builder_str() {
		var builder = CloudTraceSpanContext.builder("463ac35c9f6413ad48485a3953bb6124", "05e3ac9a4f6e3b90");

		var ctx = builder.build();

		assertEquals(OptionalLong.of(5060571933882717101L), ctx.traceIdHigh());
		assertEquals(5208512171318403364L, ctx.traceIdLow());
		assertEquals(424372568660523920L, ctx.spanId());
		assertEquals("463ac35c9f6413ad48485a3953bb6124", ctx.toTraceId());
	}

	@Test
	public void builder_long() {
		var builder = CloudTraceSpanContext.builder(OptionalLong.of(123L), 321L, 987L);

		var ctx = builder.build();

		assertEquals(OptionalLong.of(123L), ctx.traceIdHigh());
		assertEquals(321L, ctx.traceIdLow());
		assertEquals(987L, ctx.spanId());
		assertEquals("000000000000007b0000000000000141", ctx.toTraceId());
	}
}
