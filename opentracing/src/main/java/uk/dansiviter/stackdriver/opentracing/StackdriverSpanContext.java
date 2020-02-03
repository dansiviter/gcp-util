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
package uk.dansiviter.stackdriver.opentracing;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import io.opentracing.SpanContext;
import uk.dansiviter.stackdriver.opentracing.sampling.Sampler;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
public class StackdriverSpanContext implements SpanContext {
	private static final byte SAMPLED = 0x1;

	private final long traceIdLow, spanId;
	private final OptionalLong traceIdHigh;
	private final Map<String, String> baggage;
	private final int flags;
	private final OptionalLong parentSpanId;

	/**
	 *
	 * @param builder
	 */
	private StackdriverSpanContext(Builder builder) {
		this.traceIdLow = builder.traceIdLow;
		this.traceIdHigh = builder.traceIdHigh;
		this.spanId = builder.spanId;
		this.parentSpanId = builder.parentSpanId;
		this.baggage = Map.copyOf(builder.baggage);
		this.flags = builder.flags;
	}

	@Override
	public String toSpanId() {
		return Long.toHexString(spanId()).toLowerCase();
	}

	@Override
	public String toTraceId() {
		return HexUtil.toHex(this.traceIdHigh, this.traceIdLow);
	}

	public OptionalLong traceIdHigh() {
		return this.traceIdHigh;
	}

	public long traceIdLow() {
		return this.traceIdLow;
	}

	public long spanId() {
		return this.spanId;
	}

	public OptionalLong parentSpanId() {
		return this.parentSpanId;
	}

	public Optional<String> parentSpanIdAsString() {
		final OptionalLong parentSpanId = parentSpanId();
		if (parentSpanId.isPresent()) {
			return Optional.of(HexUtil.toHex(parentSpanId.getAsLong()).toLowerCase());
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
	 *
	 * @return
	 */
	public boolean sampled() {
		return flag(SAMPLED);
	}

	// --- Static Methods ---

	/**
	 *
	 * @return
	 */
	private static long randomId() {
		return ThreadLocalRandom.current().nextLong();
	}

	/**
	 *
	 * @return
	 */
	public static Builder builder() {
		return builder(OptionalLong.of(randomId()), randomId(), randomId());
	}

	/**
	 *
	 * @param parent
	 * @return
	 */
	public static Builder builder(Optional<StackdriverSpanContext> parent, Sampler sampler) {
		return builder(
				parent.map(StackdriverSpanContext::traceIdHigh).orElse(OptionalLong.of(randomId())),
				parent.map(StackdriverSpanContext::traceIdLow).orElse(randomId()), randomId())
			.sampled(sampler.test(parent));
	}

	/**
	 *
	 * @param traceId
	 * @param spanId
	 * @return
	 */
	public static Builder builder(String traceId, String spanId) {
		return new Builder(traceId, spanId);
	}

	/**
	 *
	 * @param traceIdHigh
	 * @param traceIdLow
	 * @param spanId
	 * @return
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

		private Builder(String traceId, String spanId) {
			if (traceId.length() < 1 || traceId.length() > 32) {
				throw new IllegalArgumentException("Invalid token! Must be  1 > t <= 32. [" + traceId + "]");
			}
			final String high = traceId.substring(0, Math.max(traceId.length() - 16, 0));
			final String low = traceId.substring(Math.max(traceId.length() - 16, 0), traceId.length());

			this.traceIdHigh = high.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseUnsignedLong(high, 16));
			this.traceIdLow = Long.parseUnsignedLong(low, 16);
			this.spanId = Long.parseUnsignedLong(spanId, 16);
		}

		private Builder(OptionalLong traceIdHigh, long traceIdLow, long spanId) {
			this.traceIdHigh = traceIdHigh;
			this.traceIdLow = traceIdLow;
			this.spanId = spanId;
		}

		public Builder parentSpanId(long parentSpanId) {
			this.parentSpanId = OptionalLong.of(parentSpanId);
			return this;
		}

		public Builder parentSpanId(@Nonnull String parentSpanId) {
			return parentSpanId(Long.parseLong(parentSpanId));
		}

		/**
		 *
		 * @param key
		 * @param value
		 * @return
		 */
		public Builder addBaggage(String key, String value) {
			this.baggage.put(key, value);
			return this;
		}

		/**
		 *
		 * @param enabled
		 * @return
		 */
		public Builder sampled(boolean enabled) {
			return flag(SAMPLED, enabled);
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
		 *
		 * @return
		 */
		public StackdriverSpanContext build() {
			return new StackdriverSpanContext(this);
		}
	}
}
