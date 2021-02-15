package uk.dansiviter.gcp.tracing.opentelemetry.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.dansiviter.gcp.tracing.opentelemetry.propagation.CloudTraceContextPropagator.cloudTraceContextPropagator;
import static uk.dansiviter.gcp.tracing.opentelemetry.propagation.MapCarrier.mapCarrier;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;

/**
 * Unit test for {@link CloudTraceContextPropagator}.
 */
public class CloudTraceContextPropagatorTest {
    @Test
    public void inject() {
        var map = new HashMap<String, String>();

        var spanContext = SpanContext.createFromRemoteParent("1c8b8459a164de3b2d4e82ce09f49bfa", "3d7dacabb375a261", TraceFlags.getSampled(), TraceState.getDefault());
        var context = Context.current().with(Span.wrap(spanContext));
        cloudTraceContextPropagator().inject(context, map, mapCarrier());

        assertEquals("1c8b8459a164de3b2d4e82ce09f49bfa/3d7dacabb375a261;o=1", map.get(CloudTraceContextPropagator.HEADER));
    }

    @Test
    public void extract() {
        var map = new HashMap<String, String>();
        map.put(CloudTraceContextPropagator.HEADER, "1c8b8459a164de3b2d4e82ce09f49bfa/3d7dacabb375a261;o=1");

        var context = cloudTraceContextPropagator().extract(Context.current(), map, mapCarrier());
        var spanContext = Span.fromContext(context).getSpanContext();

        assertEquals("3d7dacabb375a261", spanContext.getSpanId());
        assertEquals("1c8b8459a164de3b2d4e82ce09f49bfa", spanContext.getTraceId());
        assertTrue(spanContext.isSampled());
    }
}
