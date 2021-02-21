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
 *
 */
@Plugin(name = "Decorator", category = Node.CATEGORY, printObject = true)
public final class DecoratorItem {
    private final String clazz;

    /**
     *
     * @param clazz
     */
    public DecoratorItem(String clazz) {
        this.clazz = clazz;
    }

    public String getClazz() {
        return clazz;
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<DecoratorItem> {
        @PluginBuilderAttribute(value = "class")
        @Required(message = "Class must be provided")
        private String clazz;

        public Builder setClazz(String clazz) {
            this.clazz = clazz;
            return this;
        }

        @Override
        public DecoratorItem build() {
            return new DecoratorItem(clazz);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        return Objects.equals(clazz, ((DecoratorItem) obj).clazz);
    }
}
