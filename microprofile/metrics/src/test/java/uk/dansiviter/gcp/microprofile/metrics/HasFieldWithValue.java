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
package uk.dansiviter.gcp.microprofile.metrics;

import static org.hamcrest.Condition.matched;
import static org.hamcrest.Condition.notMatched;
import static uk.dansiviter.gcp.microprofile.metrics.ReflectionUtil.findField;

import java.lang.reflect.Field;

import org.hamcrest.Condition;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Matcher that asserts that a {@link Field} on an object meets the provided matcher.
 */
public class HasFieldWithValue<T> extends TypeSafeDiagnosingMatcher<T> {
	private final String propertyName;
	private final Matcher<Object> valueMatcher;
	private final String messageFormat;

	public HasFieldWithValue(String propertyName, Matcher<?> valueMatcher) {
		this(propertyName, valueMatcher, " field '%s' ");
	}

	public HasFieldWithValue(String propertyName, Matcher<?> valueMatcher, String messageFormat) {
		this.propertyName = propertyName;
		this.valueMatcher = nastyGenericsWorkaround(valueMatcher);
		this.messageFormat = messageFormat;
	}

	@Override
	public boolean matchesSafely(T bean, Description mismatch) {
		return propertyOn(bean, mismatch).and(withPropertyValue(bean)).matching(valueMatcher,
				String.format(messageFormat, propertyName));
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("hasField(").appendValue(propertyName).appendText(", ").appendDescriptionOf(valueMatcher)
				.appendText(")");
	}

	private Condition<Field> propertyOn(T bean, Description mismatch) {
			var field = findField(bean.getClass(), propertyName, null);
			if (field == null || !field.trySetAccessible()) {
				mismatch.appendText("No field \"" + propertyName + "\"");
				return notMatched();
			}
			return matched(field, mismatch);
	}

	private Condition.Step<Field, Object> withPropertyValue(final T bean) {
		return new Condition.Step<Field, Object>() {
			@Override
			public Condition<Object> apply(Field readField, Description mismatch) {
				try {
					return matched(readField.get(bean), mismatch);
				} catch (IllegalAccessException e) {
					mismatch.appendText("Calling '").appendText(readField.toString()).appendText("': ")
							.appendValue(e.getMessage());
					return notMatched();
				} catch (Exception e) {
					throw new IllegalStateException("Calling: '" + readField + "' should not have thrown " + e);
				}
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static Matcher<Object> nastyGenericsWorkaround(Matcher<?> valueMatcher) {
		return (Matcher<Object>) valueMatcher;
	}

	/**
	 * Creates a matcher that matches when the examined object has a field with the
	 * specified name whose value satisfies the specified matcher. For example:
	 *
	 * <pre>
	 * assertThat(myBean, hasField("foo", equalTo("bar"))
	 * </pre>
	 *
	 * @param propertyName the name of the JavaBean property that examined beans
	 *                     should possess
	 * @param valueMatcher a matcher for the value of the specified property of the
	 *                     examined bean
	 */
	public static <T> Matcher<T> hasField(String propertyName, Matcher<?> valueMatcher) {
		return new HasFieldWithValue<>(propertyName, valueMatcher);
	}

	/**
	 * Creates a matcher that matches when the examined object has a field with the
	 * specified name. For example:
	 *
	 * <pre>
	 * assertThat(myBean, hasField("foo"))
	 * </pre>
	 *
	 * @param propertyName the name of the JavaBean property that examined beans
	 *                     should possess
	 */
	public static <T> Matcher<T> hasField(String propertyName) {
		return new HasFieldWithValue<>(propertyName, Matchers.anything());
	}
}
