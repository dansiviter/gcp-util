/*
 * Copyright 2019-2020 Daniel Siviter
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
package uk.dansiviter.stackdriver.log;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.cloud.logging.LogEntry.Builder;

public class MaskingDecorator implements EntryDecorator {
	private final List<Pattern> patterns;

	MaskingDecorator(String... patterns) {
		this.patterns = stream(patterns).map(Pattern::compile).collect(toUnmodifiableList());
	}

	MaskingDecorator(Pattern... patterns) {
		this.patterns = asList(patterns);
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		StringBuilder msg = new StringBuilder((String) payload.get("message"));

		if (Objects.isNull(msg)) {
			return;
		}

		for (Pattern pattern : this.patterns) {
			final Matcher matcher = pattern.matcher(msg);
			matcher.reset();
			boolean result = matcher.find();
			if (result) {
				do {
					int length = matcher.end() - matcher.start();
					msg.delete(matcher.start(), matcher.end());
					for (int i = 0; i < length; i++) {
					msg.insert(matcher.start(), '*');
					}
					result = matcher.find();
				} while (result);
			}
		}
		payload.put("message", msg.toString());
	}
}
