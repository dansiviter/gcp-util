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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.google.cloud.logging.LogEntry.Builder;
import com.google.cloud.logging.LoggingEnhancer;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Severity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.dansiviter.gcp.log.Entry.Source;

/**
 * Tests for {@link Factory}.
 */
@ExtendWith(MockitoExtension.class)
class FactoryTest {
	@Test
	void logEntry(@Mock Entry entry) {
		when(entry.severity()).thenReturn(Severity.INFO);
		when(entry.timestamp()).thenReturn(Instant.EPOCH);
		when(entry.message()).thenReturn(Optional.of("foo"));

		var logEntry = Factory.logEntry(entry, emptyList());

		JsonPayload payload = logEntry.getPayload();

		var data = payload.getDataAsMap();
		assertThat(data.get("message"), equalTo("foo"));
		assertThat(data.get("context"), nullValue());
	}

	@Test
	void logEntry_error(@Mock Entry entry) {
		when(entry.severity()).thenReturn(Severity.ERROR);
		when(entry.timestamp()).thenReturn(Instant.EPOCH);
		when(entry.message()).thenReturn(Optional.of("foo"));
		when(entry.thrown()).thenReturn(Optional.of(() -> "Exception"));

		var logEntry = Factory.logEntry(entry, emptyList());

		JsonPayload payload = logEntry.getPayload();

		var data = payload.getDataAsMap();
		assertThat(data.get("@type"), equalTo("type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent"));
		assertThat(data.get("stack_trace"), equalTo("Exception"));
}

	@Test
	@SuppressWarnings("unchecked")
	void logEntry_context_reportLocation(@Mock Entry entry, @Mock Source source) {
		when(entry.severity()).thenReturn(Severity.WARNING);
		when(entry.timestamp()).thenReturn(Instant.EPOCH);
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
		assertThat(reportLocation.get("filePath"), equalTo("fooClass"));
		assertThat(reportLocation.get("functionName"), equalTo("fooMethod"));
		assertThat(reportLocation.get("lineNumber"), equalTo(3d));
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
		assertThat(actual, not(equalTo(actual0)));

		assertThrows(IllegalArgumentException.class, () -> Factory.instance("foo"));
	}

	@Test
	void decorator() {
		var decorator = Factory.decorator(TestDecorator.class.getName());
		assertNotNull(decorator);

		decorator = Factory.decorator(TestEnhancer.class.getName());
		assertThat(decorator, notNullValue());
		assertThat(decorator, isA(EntryDecorator.class));

		var nonDecorator = MyTestClass.class.getName();
		assertThrows(IllegalStateException.class, () -> Factory.decorator(nonDecorator));
	}

	@Test
	void decorators() {
		var decorators = Factory.decorators(TestDecorator.class.getName() + ',' + TestEnhancer.class.getName());
		assertThat(decorators.get(0), isA(EntryDecorator.class));
		assertThat(decorators.get(1), isA(EntryDecorator.class));
	}

	@Test
	void decorators_empty() {
		var decorators = Factory.decorators("");
		assertThat(decorators.isEmpty(), equalTo(true));
	}

	public static class MyTestClass { }

	public static class TestDecorator implements EntryDecorator {

		@Override
		public void decorate(Builder b, Entry e, Map<String, Object> payload) {
			// nothing to see here
		}
	}

	public static class TestEnhancer implements LoggingEnhancer {

		@Override
		public void enhanceLogEntry(Builder builder) {
			// nothing to see here
		}
	}
}
