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
package uk.dansiviter.gcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HexUtil}.
 */
public class HexUtilTest {
	@Test
	void toHex() {
		var actual = HexUtil.toHex(123);

		assertThat(actual, equalTo("000000000000007b"));
	}

	@Test
	void toHex_high() {
		var actual = HexUtil.toHex(OptionalLong.of(321), 123);

		assertThat(actual, equalTo("0000000000000141000000000000007b"));
	}
}
