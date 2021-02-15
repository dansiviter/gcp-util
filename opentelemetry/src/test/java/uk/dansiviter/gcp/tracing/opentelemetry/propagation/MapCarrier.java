package uk.dansiviter.gcp.tracing.opentelemetry.propagation;

import java.util.Map;

import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;

class MapCarrier implements Getter<Map<String, String>>, Setter<Map<String, String>> {
    private static final MapCarrier INSTANCE = new MapCarrier();

    private MapCarrier() { }

    @Override
    public String get(Map<String, String> carrier, String key) {
        return carrier.get(key);
    }

    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
        return carrier.keySet();
    }

    @Override
    public void set(Map<String, String> carrier, String key, String value) {
        carrier.put(key, value);
    }

    public static MapCarrier mapCarrier() {
        return INSTANCE;
    }
}
