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
package uk.dansiviter.gcp.opentracing;

import static java.lang.Math.abs;
import static uk.dansiviter.gcp.HexUtil.toHex;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;

import javax.annotation.Nonnull;

import io.opentracing.SpanContext;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class CloudTraceSpanContext implements SpanContext {
	private static final byte SAMPLED = 0x1;
	private static final SecureRandom RAND = new SecureRandom();

	private final long traceIdLow, spanId;
	private final OptionalLong traceIdHigh;
	private final Map<String, String> baggage;
	private final int flags;
	private final OptionalLong parentSpanId;

	private CloudTraceSpanContext(Builder builder) {
		this.traceIdLow = builder.traceIdLow;
		this.traceIdHigh = builder.traceIdHigh;
		this.spanId = builder.spanId;
		this.parentSpanId = builder.parentSpanId;
		this.baggage = Map.copyOf(builder.baggage);
		this.flags = builder.flags;
	}

	@Override
	public String toSpanId() {
		return toHex(spanId());
	}

	@Override
	public String toTraceId() {
		return toHex(this.traceIdHigh, this.traceIdLow);
	}

	/**
	 * @return high trace id.
	 */
	public OptionalLong traceIdHigh() {
		return this.traceIdHigh;
	}

	/**
	 * @return low trace id.
	 */
	public long traceIdLow() {
		return this.traceIdLow;
	}

	/**
	 * @return span id.
	 */
	public long spanId() {
		return this.spanId;
	}

	/**
	 * @return parent span id.
	 */
	public OptionalLong parentSpanId() {
		return this.parentSpanId;
	}

	/**
	 * @return parent span id.
	 */
	public Optional<String> toParentSpanId() {
		var parentSpanId = parentSpanId();
		if (parentSpanId.isPresent()) {
			return Optional.of(toHex(parentSpanId.getAsLong()).toLowerCase());
		}
		return Optional.empty();
	}

	@Override
	public Iterable<Entry<String, String>> baggageItems() {
		return this.baggage.entrySet();
	}

	private boolean flag(int mask) {
		return (this.flags & mask) != 0;
	}

	/**
	 * @return sampled flag.
	 */
	public boolean sampled() {
		return flag(SAMPLED);
	}


	// --- Static Methods ---

	/**
	 * @return random id.
	 */
	private static long randomId() {
		return abs(RAND.nextLong());
	}

	/**
	 * @return new builder instance.
	 */
	public static Builder builder() {
		return builder(OptionalLong.of(randomId()), randomId(), randomId());
	}

	/**
	 *
	 * @param parent parent context.
	 * @param sampler sampler.
	 * @return new builder instance.
	 */
	public static Builder builder(Optional<CloudTraceSpanContext> parent, @Nonnull Sampler sampler) {
		return builder(
				parent.map(CloudTraceSpanContext::traceIdHigh).orElse(OptionalLong.of(randomId())),
				parent.map(CloudTraceSpanContext::traceIdLow).orElse(randomId()), randomId())
			.sampled(sampler.test(parent));
	}

	/**
	 *
	 * @param traceId trace id.
	 * @param spanId span id.
	 * @return new builder instance.
	 */
	public static Builder builder(@Nonnull String traceId, String spanId) {
		return new Builder(traceId, spanId);
	}

	/**
	 *
	 * @param traceIdHigh high trace id.
	 * @param traceIdLow low trace id.
	 * @param spanId span id.
	 * @return new builder instance.
	 */
	public static Builder builder(OptionalLong traceIdHigh, long traceIdLow, long spanId) {
		return new Builder(traceIdHigh, traceIdLow, spanId);
	}

	// --- Inner Classes ---

	/**
	 *
	 */
	public static class Builder {
		private final long traceIdLow, spanId;
		private final OptionalLong traceIdHigh;
		private final Map<String, String> baggage = new HashMap<>();
		private OptionalLong parentSpanId = OptionalLong.empty();
		private int flags;

		private Builder(@Nonnull String traceId, @Nonnull String spanId) {
			if (traceId.length() < 1 || traceId.length() > 32) {
				throw new IllegalArgumentException("Invalid token! Must be  1 > t <= 32. [" + traceId + "]");
			}
			var high = traceId.substring(0, Math.max(traceId.length() - 16, 0));
			var low = traceId.substring(Math.max(traceId.length() - 16, 0), traceId.length());

			this.traceIdHigh = high.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseUnsignedLong(high, 16));
			this.traceIdLow = Long.parseUnsignedLong(low, 16);
			this.spanId = Long.parseUnsignedLong(spanId, 16);
		}

		private Builder(OptionalLong traceIdHigh, long traceIdLow, long spanId) {
			this.traceIdHigh = traceIdHigh;
			this.traceIdLow = traceIdLow;
			this.spanId = spanId;
		}

		/**
		 * @param parentSpanId parent span id.
		 * @return this builder instance.
		 */
		public Builder parentSpanId(long parentSpanId) {
			this.parentSpanId = OptionalLong.of(parentSpanId);
			return this;
		}

		/**
		 * @param parentSpanId parent span id.
		 * @return this builder instance.
		 */
		public Builder parentSpanId(@Nonnull String parentSpanId) {
			return parentSpanId(Long.parseLong(parentSpanId));
		}

		/**
		 *
		 * @param key baggage key.
		 * @param value baggage value.
		 * @return this builder instance.
		 */
		public Builder addBaggage(@Nonnull String key, @Nonnull String value) {
			this.baggage.put(key, value);
			return this;
		}

		/**
		 * @param sampled sampled flag.
		 * @return this builder instance.
		 */
		public Builder sampled(boolean sampled) {
			return flag(SAMPLED, sampled);
		}

		private Builder flag(byte flag, boolean enabled) {
			if (enabled) {
				this.flags = (byte) (this.flags | flag);
			} else {
				this.flags = (byte) (this.flags & ~flag);
			}
			return this;
		}

		/**
		 * @return new span context.
		 */
		public CloudTraceSpanContext build() {
			return new CloudTraceSpanContext(this);
		}
	}
}
