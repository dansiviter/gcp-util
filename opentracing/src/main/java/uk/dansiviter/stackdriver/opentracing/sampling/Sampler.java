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
package uk.dansiviter.stackdriver.opentracing.sampling;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Math.abs;
import static java.lang.Math.round;

import java.util.Optional;
import java.util.function.Predicate;

import uk.dansiviter.stackdriver.opentracing.StackdriverSpanContext;

/**
 * @author Daniel Siviter
 * @since v1.0 [13 Dec 2019]
 */
@FunctionalInterface
public interface Sampler extends Predicate<Optional<StackdriverSpanContext>> {
	/** Always sample. */
	Sampler ALWAYS = (p) -> true;
	/** Never sample. */
	Sampler NEVER = (p) -> false;

	/** Default sampler which samples ~1% which parent span overriding. */
	Sampler DEFAULT = parentOverriding(probablistic(.01));

	/**
	 *
	 * @return
	 */
	public static Sampler alwaysSample() {
		return ALWAYS;
	}

	/**
	 *
	 * @return
	 */
	public static Sampler neverSample() {
		return NEVER;
	}


	/**
	 *
	 * @return
	 */
	public static Sampler defaultSampler() {
		return DEFAULT;
	}

	/**
	 *
	 * @param probability
	 * @return
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
		return new Sampler() {
			@Override
			public boolean test(Optional<StackdriverSpanContext> ctx) {
				return ctx.map(c -> abs(c.traceIdLow()) < upper).orElse(false);
			}
		};
	}

	/**
	 *
	 * @param sampler
	 * @return
	 */
	public static Sampler parent() {
		return p -> p.isPresent() ? p.get().sampled() : false;
	}

	/**
	 *
	 * @param sampler
	 * @return
	 */
	public static Sampler parentOverriding(Sampler sampler) {
		return p -> p.isPresent() ? p.get().sampled() : sampler.test(p);
	}
}
