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
package uk.dansiviter.gcp.log.log4j2;

import java.util.Objects;

import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

/**
 * Decorator item.
 */
@Plugin(name = "Decorator", category = Node.CATEGORY, printObject = true)
public final class DecoratorItem {
    private final String className;

    /**
     * @param builder the builder instance.
     */
    public DecoratorItem(Builder builder) {
        this.className = builder.className;
    }

    /**
     * @return the class name.
     */
    public String getClassName() {
        return this.className;
    }

    /**
     * @return new builder instance.
     */
    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.className);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        return Objects.equals(this.className, ((DecoratorItem) obj).className);
    }


    // --- Inner Classes ---

    /**
     * Decorator item builder.
     */
    public static class Builder implements org.apache.logging.log4j.core.util.Builder<DecoratorItem> {
        @PluginBuilderAttribute(value = "class")
        @Required(message = "Class must be provided")
        private String className;

        /**
         * @param className the name of the class.
         * @return this builder instance.
         */
        public Builder setClassName(String className) {
            this.className = className;
            return this;
        }

        @Override
        public DecoratorItem build() {
            return new DecoratorItem(this);
        }
    }
}
