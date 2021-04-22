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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DecoratorItem}.
 */
class DecoratorItemTest {
	@Test
	void init() {
		var decoratorItem0 = DecoratorItem.newBuilder().setClassName(DecoratorItemTest.class.getName()).build();
		assertThat(decoratorItem0.getClassName(), equalTo(DecoratorItemTest.class.getName()));

		var decoratorItem1 = DecoratorItem.newBuilder().setClassName(DecoratorItemTest.class.getName()).build();
		assertThat(decoratorItem0, is(decoratorItem0));
		assertThat(decoratorItem0, is(decoratorItem1));
		assertThat(decoratorItem0, not(new Object()));
		assertThat(decoratorItem0.hashCode(), is(decoratorItem1.hashCode()));
	}
}
