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
package uk.dansiviter.gcp.log.opentelemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Optional;

import com.google.cloud.logging.LogEntry.Builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import uk.dansiviter.gcp.log.Entry;


/**
 * Tests for {@link Decorator}.
 */
@ExtendWith(MockitoExtension.class)
class DecoratorTest {

	@Test
	void decorate(@Mock Builder b, @Mock Entry e, @Mock Span span) {
		var payload = new HashMap<String, Object>();

		when(span.storeInContext(any())).thenCallRealMethod();
		var spanCtx = SpanContext.create("00000000000000000000000000000001", "0000000000000002", TraceFlags.getSampled(), TraceState.getDefault());
		when(span.getSpanContext()).thenReturn(spanCtx);
		try (var scope = span.storeInContext(Context.current()).makeCurrent()) {
			new Decorator(Optional.of("foo")).decorate(b, e, payload);
		}

		verify(b).setSpanId("0000000000000002");
		verify(b).setTrace("projects/foo/traces/00000000000000000000000000000001");
		verify(b).setTraceSampled(true);
	}

	@Test
	void decorate_noContext(@Mock Builder b, @Mock Entry e) {
		var payload = new HashMap<String, Object>();

		new Decorator(Optional.of("foo")).decorate(b, e, payload);

		verifyNoInteractions(b);
	}
}
