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
package uk.dansiviter.gcp.log.jul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BasicFormatter}.
 */
@ExtendWith(MockitoExtension.class)
public class BasicFormatterTest {
	@InjectMocks
	private BasicFormatter formatter;

	@Test
	public void format(@Mock LogRecord record) {
		Object[] params = { "acme", (Supplier<?>) () -> "foo" };
		when(record.getParameters()).thenReturn(params);
		when(record.getMessage()).thenReturn("Hello {0} [{1}]");

		String actual = formatter.format(record);

		assertEquals("Hello acme [foo]", actual);
	}
}
