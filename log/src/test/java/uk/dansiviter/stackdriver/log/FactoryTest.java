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
package uk.dansiviter.stackdriver.log;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.stackdriver.log.Entry.Source;

/**
 *
 */
@ExtendWith(MockitoExtension.class)
public class FactoryTest {
	@Test
	public void logEntry(@Mock Entry entry) {
		when(entry.severity()).thenReturn(Severity.INFO);
		when(entry.message()).thenReturn(Optional.of("foo"));

		LogEntry logEntry = Factory.logEntry(entry, emptyList());

		JsonPayload payload = logEntry.getPayload();

		Map<String, Object> data = payload.getDataAsMap();
		assertEquals("foo", data.get("message"));
		assertNull(data.get("context"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void logEntry_context_reportLocation(@Mock Entry entry, @Mock Source source) {
		when(entry.severity()).thenReturn(Severity.WARNING);
		when(entry.thrown()).thenReturn(Optional.of(() -> "thrown"));
		when(entry.source()).thenReturn(Optional.of(source));
		when(source.className()).thenReturn("fooClass");
		when(source.method()).thenReturn("fooMethod");
		when(source.line()).thenReturn(OptionalInt.of(3));
		doCallRealMethod().when(source).asMap();
		LogEntry logEntry = Factory.logEntry(entry, emptyList());

		JsonPayload payload = logEntry.getPayload();

		Map<String, Object> data = payload.getDataAsMap();
		Map<String, Object> context = (Map<String, Object>) data.get("context");
		Map<String, Object> reportLocation = (Map<String, Object>) context.get("reportLocation");
		assertEquals("fooClass", reportLocation.get("filePath"));
		assertEquals("fooMethod", reportLocation.get("functionName"));
		assertEquals(3d, reportLocation.get("lineNumber"));
	}
}
