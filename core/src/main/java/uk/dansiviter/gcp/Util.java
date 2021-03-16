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
package uk.dansiviter.gcp;

import static java.util.function.UnaryOperator.identity;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [5 Feb 2020]
 */
public enum Util { ;
	/**
	 * Creates a {@link ThreadLocal} that uses can supply and initial value
	 *
	 * @param <T> the type to store.
	 * @param initial the initial value.
	 * @return the thread local instance.
	 */
	public static <T> ThreadLocal<T> threadLocal(Supplier<T> initial) {
		return threadLocal(initial, identity());
	}

	/**
	 * Creates a {@link ThreadLocal} that uses can supply and initial value and an operator to reset on each call. This
	 * is useful for factories were creating new instances is potentially wasteful and help reduce garbage.
	 *
	 * @param <T> the type to store.
	 * @param initial the initial value.
	 * @param reset the reset function.
	 * @return the tread local instance.
	 */
	public static <T> ThreadLocal<T> threadLocal(Supplier<T> initial, UnaryOperator<T> reset) {
		return new ThreadLocal<T>() {
			@Override
			protected T initialValue() {
				return initial.get();
			}

			@Override
			public T get() {
				return reset.apply(super.get());
			}
		};
	}
}
