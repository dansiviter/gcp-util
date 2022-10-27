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
package uk.dansiviter.gcp.microprofile.metrics;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Used for annotating {@link java.util.function.Predicate} to filter out metrics to be sent to Cloud Monitoring. For
 * example, to not send metrics starting with 'foo':
 * <pre>
 * &#064;Filter
 * &#064;Produces
 * public Predicate&lt;MetricID&gt; filter() {
 *   return id -&gt; id.getName().startsWith("foo");
 * }
 * </pre>
 */
@Qualifier
@Retention(RUNTIME)
@Target({ METHOD, FIELD, PARAMETER, TYPE})
public @interface Filter {
    /**
     * Supports inline instantiation of the {@link Filter} annotation.
     */
		@SuppressWarnings("all")
    public static final class Literal extends AnnotationLiteral<Filter> implements Filter {
			private static final long serialVersionUID = 1L;

			public static final Literal INSTANCE = new Literal();
	}
}
