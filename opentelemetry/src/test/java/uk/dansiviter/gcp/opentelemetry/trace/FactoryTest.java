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
package uk.dansiviter.gcp.opentelemetry.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.Timestamp;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Factory}.
 */
public class FactoryTest {
    @Test
    public void toTimestamp() {
        Instant i = Instant.now();
        long epochNanos = TimeUnit.SECONDS.toNanos(i.getEpochSecond()) + i.getNano();
        Timestamp timestamp = Factory.toTimestamp(epochNanos);

        assertEquals(i.getEpochSecond(), timestamp.getSeconds());
        assertEquals(i.getNano(), timestamp.getNanos());
    }
}
