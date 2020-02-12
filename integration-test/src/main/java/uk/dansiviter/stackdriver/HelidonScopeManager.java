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
package uk.dansiviter.stackdriver;

import static io.helidon.common.context.Contexts.context;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

/**
 * A scope manager that uses {@link io.helidon.common.context.Context} for storage.
 *
 * @author Daniel Siviter
 * @since v1.0 [12 Feb 2020]
 */
public class HelidonScopeManager implements ScopeManager {
	@Override
	public HelidonScope activate(Span span) {
		return activate(span, false);
	}

	@Override
	public HelidonScope active() {
		return context().flatMap(c -> c.get(HelidonScope.class)).orElse(null);
	}

	@Override
	public Span activeSpan() {
		final HelidonScope scope = active();
		return scope == null ? null : scope.span();
	}

	@Override
	public HelidonScope activate(Span span, boolean finishSpanOnClose) {
		return new HelidonScope(span, finishSpanOnClose);
	}

	private void set(HelidonScope scope) {
		if (scope == null) {
			return;
		}
		context().ifPresent(c -> c.register(scope));
	}


	// --- Inner Classes ---

	private class HelidonScope implements Scope {
		private final Span delegate;
		private final boolean finishOnClose;
		private final HelidonScope prev;

		HelidonScope(Span delegate, boolean finishOnClose) {
			this.delegate = delegate;
			this.finishOnClose = finishOnClose;
			this.prev = active();
			set(this);
		}

		@Override
		public void close() {
			if (active() != this) {
				return;
			}
			if (finishOnClose) {
				this.delegate.finish();
			}
			set(this.prev);
		}

		@Override
		public Span span() {
			return this.delegate;
		}
	}
}
