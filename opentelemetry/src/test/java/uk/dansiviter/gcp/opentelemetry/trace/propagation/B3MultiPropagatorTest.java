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
package uk.dansiviter.gcp.opentelemetry.trace.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.dansiviter.gcp.opentelemetry.trace.propagation.B3MultiPropagator.b3MultiPropagator;
import static uk.dansiviter.gcp.opentelemetry.trace.propagation.MapCarrier.mapCarrier;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;

/**
 * Unit test for {@link B3MultiPropagator}.
 */
public class B3MultiPropagatorTest {
    @Test
    public void inject() {
        var map = new HashMap<String, String>();

        var spanContext = SpanContext.createFromRemoteParent("1c8b8459a164de3b2d4e82ce09f49bfa", "3d7dacabb375a261", TraceFlags.getSampled(), TraceState.getDefault());
        var context = Context.current().with(Span.wrap(spanContext));
        b3MultiPropagator().inject(context, map, mapCarrier());

        assertEquals("1c8b8459a164de3b2d4e82ce09f49bfa", map.get(B3MultiPropagator.TRACE_ID));
        assertEquals("3d7dacabb375a261", map.get(B3MultiPropagator.SPAN_ID));
        assertEquals("1", map.get(B3MultiPropagator.SAMPLED));
    }

    @Test
    public void extract() {
        var map = new HashMap<String, String>();
        map.put(B3MultiPropagator.TRACE_ID, "1c8b8459a164de3b2d4e82ce09f49bfa");
        map.put(B3MultiPropagator.SPAN_ID, "3d7dacabb375a261");
        map.put(B3MultiPropagator.SAMPLED, "1");

        var context = b3MultiPropagator().extract(Context.current(), map, mapCarrier());
        var spanContext = Span.fromContext(context).getSpanContext();

        assertEquals("3d7dacabb375a261", spanContext.getSpanId());
        assertEquals("1c8b8459a164de3b2d4e82ce09f49bfa", spanContext.getTraceId());
        assertTrue(spanContext.isSampled());
    }
}
