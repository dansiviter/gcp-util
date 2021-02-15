package uk.dansiviter.gcp.tracing.opentelemetry;

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
