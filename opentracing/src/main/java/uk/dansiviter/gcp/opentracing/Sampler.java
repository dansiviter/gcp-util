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

import static java.lang.Long.MAX_VALUE;
import static java.lang.Math.abs;
import static java.lang.Math.round;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
@FunctionalInterface
public interface Sampler extends Predicate<Optional<CloudTraceSpanContext>> {
	/** Always sample. */
	Sampler ALWAYS = p -> true;
	/** Never sample. */
	Sampler NEVER = p -> false;

	/** Default sampler which samples ~1% which parent span overriding. */
	Sampler DEFAULT = parentOverriding(probablistic(.01));

	/**
	 * @return always sampler instance.
	 */
	public static Sampler alwaysSample() {
		return ALWAYS;
	}

	/**
	 * @return never sample instance.
	 */
	public static Sampler neverSample() {
		return NEVER;
	}


	/**
	 * @return default sampler instance.
	 */
	public static Sampler defaultSampler() {
		return DEFAULT;
	}

	/**
	 * @param probability the probability of sampling.
	 * @return sampler instance.
	 */
	public static Sampler probablistic(double probability) {
		final long upper;
		if (probability == 0.0) {
			upper = -1;
		} else if (probability == 1.0) {
			upper = MAX_VALUE;
		} else {
			upper = round(probability * MAX_VALUE);
		}
		return ctx -> ctx.map(c -> abs(c.traceIdLow()) < upper).orElse(false);
	}

	/**
	 * @return a sampler that uses the parent context to decide.
	 */
	public static Sampler parent() {
		return p -> p.map(CloudTraceSpanContext::sampled).orElse(false);
	}

	/**
	 * @param sampler sampler that is overriden by the parent context.
	 * @return sampler instance.
	 */
	public static Sampler parentOverriding(Sampler sampler) {
		return p -> p.isPresent() ? p.get().sampled() : sampler.test(p);
	}
}
