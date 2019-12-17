/*
 * Copyright 2019 Daniel Siviter
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

import java.util.Optional;
import java.util.function.Predicate;

import uk.dansiviter.stackdriver.opentracing.StackdriverSpanContext;

/**
 *
 */
@FunctionalInterface
public interface Sampler extends Predicate<Optional<StackdriverSpanContext>> {
	Sampler ALWAYS = (p) -> true;
	Sampler NEVER = (p) -> false;

	/**
	 *
	 * @return
	 */
	public static Sampler always() {
		return ALWAYS;
	}

	/**
	 *
	 * @return
	 */
	public static Sampler never() {
		return NEVER;
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
