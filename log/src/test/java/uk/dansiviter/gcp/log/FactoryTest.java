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
package uk.dansiviter.gcp.log;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.gcp.log.Entry.Source;

/**
 * Unit test for {@link Factory}.
 */
@ExtendWith(MockitoExtension.class)
class FactoryTest {
	@Test
	void logEntry(@Mock Entry entry) {
		when(entry.severity()).thenReturn(Severity.INFO);
		when(entry.message()).thenReturn(Optional.of("foo"));

		var logEntry = Factory.logEntry(entry, emptyList());

		JsonPayload payload = logEntry.getPayload();

		var data = payload.getDataAsMap();
		assertEquals("foo", data.get("message"));
		assertNull(data.get("context"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void logEntry_context_reportLocation(@Mock Entry entry, @Mock Source source) {
		when(entry.severity()).thenReturn(Severity.WARNING);
		when(entry.thrown()).thenReturn(Optional.empty());
		when(entry.source()).thenReturn(Optional.of(source));
		when(source.className()).thenReturn("fooClass");
		when(source.method()).thenReturn("fooMethod");
		when(source.line()).thenReturn(OptionalInt.of(3));
		doCallRealMethod().when(source).asMap();
		var logEntry = Factory.logEntry(entry, emptyList());

		JsonPayload payload = logEntry.getPayload();

		var data = payload.getDataAsMap();
		var context = (Map<String, Object>) data.get("context");
		var reportLocation = (Map<String, Object>) context.get("reportLocation");
		assertEquals("fooClass", reportLocation.get("filePath"));
		assertEquals("fooMethod", reportLocation.get("functionName"));
		assertEquals(3d, reportLocation.get("lineNumber"));
	}

	@Test
	void toCharSequence_throwable() {
		var actual = Factory.toCharSequence(new Throwable("Oh no!")).toString();
		assertThat(actual, startsWith("java.lang.Throwable: Oh no!\n" +
				"\tat uk.dansiviter.gcp.log.FactoryTest.toCharSequence_throwable(FactoryTest.java:"));
	}

	@Test
	void instance() {
		var actual = Factory.instance(MyTestClass.class.getName());
		assertNotNull(actual);

		var actual0 = Factory.instance(MyTestClass.class.getName());
		assertNotEquals(actual, actual0);
	}

	public static class MyTestClass { }
}
