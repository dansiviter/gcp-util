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

import jakarta.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

/**
 * {@link AnnotationLiteral} for {@link RegistryType}.
 *
 * @author Daniel Siviter
 * @since v1.0 [6 Nov 2019]
 */
@SuppressWarnings("all")
public final class RegistryTypeLiteral extends AnnotationLiteral<RegistryType> implements RegistryType {
  private static final long serialVersionUID = 1L;

	private static final RegistryTypeLiteral BASE_TYPE = new RegistryTypeLiteral(Type.BASE);
	private static final RegistryTypeLiteral VENDOR_TYPE = new RegistryTypeLiteral(Type.VENDOR);
	private static final RegistryTypeLiteral APPLICATION_TYPE = new RegistryTypeLiteral(Type.APPLICATION);

	/** Type */
  private final Type type;

  RegistryTypeLiteral(Type type) {
    this.type = type;
  }

  @Override
  public Type type() {
    return this.type;
  }

  /**
	 * Returns metric registry type annotation literal.
	 *
	 * @param type the type to get.
	 * @return
	 */
	public static RegistryType registryType(Type type) {
		switch (type) {
		case BASE:
			return BASE_TYPE;
		case VENDOR:
			return VENDOR_TYPE;
		case APPLICATION:
			return APPLICATION_TYPE;
		default:
			throw new IllegalArgumentException("Unknown type! [" + type + "]");
		}
	}
}
