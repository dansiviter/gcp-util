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
package uk.dansiviter.gcp.microprofile.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MetadataConfigSource}.
 */
class MetadataConfigSourceTest {
	private MetadataConfigSource source;

	@BeforeEach
	void before() {
		this.source = new MetadataConfigSource();
	}

	@Test
	void getName() {
		assertThat(this.source.getName(), equalTo("gcp-metadata-server"));
	}

	@Test
	void getOrdinal() {
		assertThat(this.source.getOrdinal(), equalTo(100));
	}

	@Test
	void getProperties() {
		assertThat(this.source.getProperties(), anEmptyMap());
	}
}
