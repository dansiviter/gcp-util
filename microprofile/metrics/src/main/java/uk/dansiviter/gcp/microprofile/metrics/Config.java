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
package uk.dansiviter.gcp.microprofile.metrics;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static org.eclipse.microprofile.metrics.MetricType.HISTOGRAM;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.google.api.Distribution.BucketOptions;
import com.google.api.Distribution.BucketOptions.Exponential;
import com.google.errorprone.annotations.Immutable;

import org.eclipse.microprofile.metrics.MetricType;

/**
 * Configuration for elements that cannot be derived.
 */
@Immutable
public class Config {
	private final Map<Entry<MetricType, String>, BucketOptions> bucketOptions;
	private final Map<String, String> labelDescriptions;

	private Config(Builder b) {
		this.bucketOptions = Map.copyOf(b.bucketOptions);
		this.labelDescriptions = Map.copyOf(b.labelDescriptions);
	}

	/**
	 * Gets an instance of bucket options for the given unit.
	 *
	 * @param type the metric type.
	 * @param unit the unit.
	 * @return the bucket options for the unit or that for {@code default}.
	 */
	public BucketOptions bucketOptions(@Nonnull MetricType type, @Nonnull String unit) {
		var options = this.bucketOptions.get(entry(type, unit));
		if (options == null) {
			options = this.bucketOptions.get(entry(type, "default"));
		}
		return requireNonNull(options);
	}

	/**
	 * @param key the label key.
	 * @return the value.
	 */
	public Optional<String> labelDescription(@Nonnull String key) {
		return Optional.ofNullable(this.labelDescriptions.get(key));
	}

	/**
	 * @return a new builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	private static BucketOptions bucketOptions(int numBuckets, double scale, double growthFactor) {
		return BucketOptions.newBuilder()
				.setExponentialBuckets(
						Exponential.newBuilder()
								.setNumFiniteBuckets(numBuckets)
								.setScale(scale)
								.setGrowthFactor(growthFactor)
								.build())
				.build();
	}

	/**
	 *
	 */
	public static class Builder {
		private final Map<Entry<MetricType, String>, BucketOptions> bucketOptions = defaultBucketOptions();
		private final Map<String, String> labelDescriptions = defaultLabelDescriptions();

		private Builder() {
		}

		/**
		 * Puts a bucket options for the given type and unit.
		 *
		 * @param type the metric type.
		 * @param unit the metric unit.
		 * @param options the bucket options.
		 * @return this builder.
		 */
		public Builder put(@Nonnull MetricType type, @Nonnull String unit, @Nonnull BucketOptions options) {
			this.bucketOptions.put(entry(type, unit), options);
			return this;
		}

		/**
		 * Puts a label.
		 *
		 * @param key label key.
		 * @param value label value.
		 * @return this builder.
		 */
		public Builder labelDescription(@Nonnull String key, @Nonnull String value) {
			this.labelDescriptions.put(key, value);
			return this;
		}

		/**
		 * @return a new config instance.
		 */
		public Config build() {
			return new Config(this);
		}

		private static Map<Entry<MetricType, String>, BucketOptions> defaultBucketOptions() {
			return Map.of(
				entry(HISTOGRAM, "default"), bucketOptions(20, 1, 2),
				entry(TIMER, "default"), bucketOptions(20, 1_000_000, 2),
				entry(TIMER, "ns"), bucketOptions(20, 1_000_000, 2),
				entry(TIMER, "us"), bucketOptions(20, 1_000, 2),
				entry(TIMER, "ms"), bucketOptions(20, 1, 2));
		}

		private static Map<String, String> defaultLabelDescriptions() {
			return Map.of(
				"response_code", "The HTTP response (status) code.",
				"path", "The HTTP request path.",
				"target_host", "The name of the target host.");
		}
	}
}
