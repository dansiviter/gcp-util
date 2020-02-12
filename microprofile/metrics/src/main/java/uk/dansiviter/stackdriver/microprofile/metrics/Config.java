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
package uk.dansiviter.stackdriver.microprofile.metrics;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static org.eclipse.microprofile.metrics.MetricType.HISTOGRAM;
import static org.eclipse.microprofile.metrics.MetricType.TIMER;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

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

	public BucketOptions bucketOptions(@Nonnull MetricType type, @Nonnull String unit) {
		BucketOptions options = this.bucketOptions.get(entry(type, unit));
		if (options == null) {
			options = this.bucketOptions.get(entry(type, "default"));
		}
		return options;
	}

	public Optional<String> labelDescription(String key) {
		return Optional.ofNullable(this.labelDescriptions.get(key));
	}

	public static Builder builder() {
		return new Builder();
	}

	private static Map<Entry<MetricType, String>, BucketOptions> defaultBucketOptions() {
		final Map<Entry<MetricType, String>, BucketOptions> map = new HashMap<>();
		map.put(entry(HISTOGRAM, "default"), bucketOptions(20, 1, 2));
		map.put(entry(TIMER, "default"), bucketOptions(20, 1_000_000, 2));
		map.put(entry(TIMER, "ns"), bucketOptions(20, 1_000_000, 2));
		map.put(entry(TIMER, "us"), bucketOptions(20, 1_000, 2));
		map.put(entry(TIMER, "ms"), bucketOptions(20, 1, 2));
		return map;
	}

	private static Map<String, String> defaultLabelDescriptions() {
		final Map<String, String> map = new HashMap<>();
		map.put("response_code", "The HTTP response (status) code.");
		map.put("path", "The HTTP request path.");
		map.put("target_host", "The name of the target host.");
		return map;
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

		public Builder set(@Nonnull MetricType type, @Nonnull String unit, @Nonnull BucketOptions options) {
			this.bucketOptions.put(entry(type, unit), requireNonNull(options));
			return this;
		}

		public Config build() {
			return new Config(this);
		}
	}
}
