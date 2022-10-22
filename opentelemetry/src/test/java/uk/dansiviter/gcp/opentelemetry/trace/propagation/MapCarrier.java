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

import java.util.Map;

import javax.annotation.Nullable;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

class MapCarrier implements TextMapGetter<Map<String, String>>, TextMapSetter<Map<String, String>> {
    private static final MapCarrier INSTANCE = new MapCarrier();

    private MapCarrier() { }

    @Override
    public String get(@Nullable Map<String, String> carrier, String key) {
        return carrier != null ?  carrier.get(key) : null;
    }

    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
        return carrier.keySet();
    }

    @Override
    public void set(@Nullable Map<String, String> carrier, String key, String value) {
        if (carrier != null) {
            carrier.put(key, value);
        }
    }

    public static MapCarrier mapCarrier() {
        return INSTANCE;
    }
}
