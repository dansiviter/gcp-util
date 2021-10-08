/*
 * Copyright 2021 Daniel Siviter
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
package uk.dansiviter.gcp.jdbccommenter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;

@ExtendWith(MockitoExtension.class)
class DriverImplTest {
	@RegisterExtension
	static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

	private final Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test");

	@Test
	void registration() {
		assertThat(Collections.list(DriverManager.getDrivers()), hasItem(isA(Driver.class)));
	}

	@Test
	void getMajorVersion() {
		assertThat(new DriverImpl().getMajorVersion(), is(-1));
	}

	@Test
	void getMinorVersion() {
		assertThat(new DriverImpl().getMinorVersion(), is(-1));
	}

	@Test
	void jdbcCompliant() {
		assertThat(new DriverImpl().jdbcCompliant(), is(true));
	}

	@Test
	void connect_noDriver() throws SQLException {
		var url = "jdbc:commenter:foo";
		var driver = DriverManager.getDriver(url);
		var ex = assertThrows(SQLException.class, () -> driver.connect(url, null));
		assertThat(ex.getMessage(), is("No suitable driver found for jdbc:foo"));
	}

	@Test
	void connect(@Mock Driver driver, @Mock Connection conn, @Mock PreparedStatement statement) throws SQLException {
		when(driver.connect(any(), any())).thenReturn(conn);
		when(conn.prepareStatement(any())).thenReturn(statement);

		var span = tracer.spanBuilder("foo").startSpan();
		try (var scope = span.makeCurrent()) {
			DriverManager.registerDriver(driver);

			var url = "jdbc:commenter:foo";
			var actualDriver = DriverManager.getDriver(url);
			var actualConn = actualDriver.connect(url, null);
			verify(driver).connect(any(), any());
			assertThat(actualConn, isProxy());

			var actualStatement = actualConn.prepareStatement("foo");
			var sql = ArgumentCaptor.forClass(String.class);
			verify(conn).prepareStatement(sql.capture());

			var ctx = span.getSpanContext();
			assertThat(sql.getValue(), is(String.format("foo /*traceparent='00-%s-%s-01'*/", ctx.getTraceId(), ctx.getSpanId())));

			assertThat(actualStatement, isProxy());
		} finally {
			DriverManager.deregisterDriver(driver);
		}
	}

	/**
	 *
	 * @param <T>
	 * @return
	 */
	private static <T> Matcher<T> isProxy() {
		return new BaseMatcher<T>(){
			@Override
			public boolean matches(Object actual) {
				return Proxy.isProxyClass(actual.getClass());
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("a proxy type");
			}
		};
	}
}
