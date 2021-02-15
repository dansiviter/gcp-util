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
package uk.dansiviter.gcp;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

/**
 * A utility to atomically initialise an instance and subsequently clear if
 * required. This is non-blocking.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Nov 2020]
 */
public class AtomicInit<T> implements AutoCloseable {
	private final AtomicReference<AtomicInit<T>> shield = new AtomicReference<>();
	private final AtomicReference<T> ref = new AtomicReference<>();
	private final AtomicBoolean closed = new AtomicBoolean();

	private final Supplier<T> supplier;

	public AtomicInit(@Nonnull Supplier<T> supplier) {
		this.supplier = requireNonNull(supplier);
	}

	/**
	 *
	 * @return the
	 */
	public T get() {
		T value;
		while ((value = this.ref.get()) == null) {
			if (isClosed()) {
				throw new IllegalStateException();
			}
			if (this.shield.compareAndSet(null, this)) {
				var instance = this.supplier.get();
				if (isNull(instance)) {
					closed.set(true);
					throw new IllegalStateException();
				}
				this.ref.set(instance);
			}
		}
		return value;
	}

	/**
	 * @return {@code true} if this has been initialised.
	 */
	public boolean isInitialised() {
		return this.shield.get() == this;
	}

	/**
	 * Performs the action if the value has been initialised.
	 *
	 * @param action the action to perform.
	 */
	public void ifInitialised(Consumer<T> action) {
		if (!isInitialised()) {
			return;
		}
		var value = get();
		if (value != null) {
			action.accept(value);
		}
	}

	/**
	 * @return {@code true} if this has been closed.
	 */
	public boolean isClosed() {
		return this.closed.get();
	}

	/**
	 * Closes removed the reference to the underlying instance if this has been
	 * initialised and, if it is an instance of {@link Closeable}, will also close
	 * it.
	 */
	@Override
	public void close() throws Exception {
		if (!isInitialised()) {
			return;
		}
		while (this.ref.get() != null) {
			if (this.closed.compareAndSet(false, true)) {
				if (this.ref.get() instanceof AutoCloseable) {
					((AutoCloseable) this.ref.get()).close();
				}
				this.ref.set(null);
			}
		}
	}


	// --- Static Methods ---

	/**
	 *
	 * @param <T>
	 * @param supplier the supplier to get an instance.
	 * @return
	 */
	public static <T> AtomicInit<T> atomic(Supplier<T> supplier) {
		return new AtomicInit<>(supplier);
	}
}
