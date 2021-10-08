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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.logging.Logger;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;

/**
 * A JDBC driver that intercepts calls to underlying driver to perform Trace comments.
 */
public class DriverImpl implements java.sql.Driver {
	private static final String PREFIX = "jdbc:commenter:";

	static {
    try {
      DriverManager.registerDriver(new DriverImpl());
    } catch (SQLException e) {
      throw new RuntimeException("Cannot register driver!", e);
    }
  }

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return url.startsWith(PREFIX);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return DriverManager.getDriver(realUrl(url)).getPropertyInfo(realUrl(url), info);
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		if (!acceptsURL(url)) {
      return null;
    }

    var realUrl = realUrl(url);
		var conn = DriverManager.getConnection(realUrl, info);
		return proxy(conn, new ConnectionHandler(conn));
  }

	private OptionalInt version(int i) {
		var version = getClass().getPackage().getImplementationVersion();
		if (version != null) {
			var tokens = version.split(".");
			if (tokens.length >= i) {
				return OptionalInt.of(Integer.parseInt(tokens[i]));
			}
		}
		return OptionalInt.empty();
	}

	@Override
	public int getMajorVersion() {
		return version(0).orElse(-1);
	}

	@Override
	public int getMinorVersion() {
		return version(1).orElse(-1);
	}

	@Override
	public boolean jdbcCompliant() {
		return true;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		throw new SQLFeatureNotSupportedException();
	}


	// *** Static Methods ***

	/**
	 * Returns the underlying driver URL.
	 *
	 * @param url the url to analyse.
	 * @return the underlying driver URL.
	 */
	private static String realUrl(String url) {
		return "jdbc:".concat(url.substring(PREFIX.length()));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(T o, InvocationHandler h) {
    return (T) Proxy.newProxyInstance(o.getClass().getClassLoader(), o.getClass().getInterfaces(), h);
	}

	private static String comment(String sql) {
		var spanCtx = Span.current().getSpanContext();

		if (!spanCtx.isValid() || !spanCtx.isSampled()) {
			return sql;
		}

		var builder = new StringBuilder(sql).append(" /*");
		appendParent(builder, spanCtx);
		appendState(builder, spanCtx.getTraceState());
		return builder.append("*/").toString();
	}

	private static void appendParent(StringBuilder builder, SpanContext spanCtx) {
		builder.append("traceparent=").append(String.format(
			"'00-%s-%s-%02X'",
			spanCtx.getTraceId(),
			spanCtx.getSpanId(),
			spanCtx.getTraceFlags().asByte()));
	}

	private static void appendState(StringBuilder builder, TraceState state) {
		if (state.isEmpty()) {
			return;
		}
		builder.append(",tracestate='");
		state.forEach((k, v) -> builder.append(k).append('=').append(v));
		builder.append('\'');
	}


	// *** Inner Classes ***

	/**
	 *
	 */
	private class ConnectionHandler implements InvocationHandler {
		final Connection conn;

		ConnectionHandler(Connection conn) {
			this.conn = conn;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if ((method.getName().equals("prepareCall") || method.getName().equals("prepareStatement"))
					&& method.getParameterTypes().length > 0
					&& method.getParameterTypes()[0] == String.class) {
				args[0] = comment((String) args[0]);
			}

			Object obj;
    	try {
      	obj =  method.invoke(conn, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}

			if (obj instanceof Statement) {
				Statement stmt = (Statement) obj;
				obj = proxy(stmt, new StatementHandler(stmt));
			}

			return obj;
		}
	}

	/**
	 *
	 */
	private class StatementHandler implements InvocationHandler {
		final Statement statement;

		StatementHandler(Statement statement) {
			this.statement = statement;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getParameterTypes().length > 0 && method.getParameterTypes()[0] == String.class) {
				args[0] = comment((String) args[0]);
			}
			try {
      	return method.invoke(this.statement, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		}
	}
}
