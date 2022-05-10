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
package uk.dansiviter.gcp.log;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.cloud.logging.LogEntry.Builder;

/**
 * A {@link EntryDecorator} that applies masking to any matching patterns. Be aware of the performance of the regex you
 * provide as this could seriously hamper the performance of the application.
 * <p>
 * If using this with a logger that does not permit constructor parameters it's recommended to create your own subclass:
 * <pre>
 * public class Ip4MaskingDecorator extends MessageMaskingDecorator {
 *   public Ip4MaskingDecorator() {
 *     super("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
 *   }
 * }
 * </pre>
 *
 * @author Daniel Siviter
 * @since v1.0 [16 Jan 2020]
 */
public class MessageMaskingDecorator implements EntryDecorator {
	private static final String REPLACEMENT = "**REDACTED**";

	private final List<Pattern> patterns;

	/**
	 * Creates a masking decorator for the given patterns.
	 *
	 * @param patterns regular expression patterns.
	 */
	public MessageMaskingDecorator(String... patterns) {
		this.patterns = stream(patterns).map(Pattern::compile).collect(toUnmodifiableList());
	}

	/**
	 * Creates a masking decorator for the given patterns.
	 *
	 * @param patterns regular expression patterns.
	 */
	public MessageMaskingDecorator(Pattern... patterns) {
		this.patterns = asList(patterns);
	}

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		var originalMsg = (String) payload.get("message");

		if (isNull(originalMsg) || originalMsg.isBlank()) {
			return;
		}

		var msg = new StringBuilder(originalMsg);
		this.patterns.forEach(p -> apply(p, msg));
		payload.put("message", msg.toString());
	}

	private static void apply(Pattern pattern, StringBuilder msg) {
		var matcher = pattern.matcher(msg);
		matcher.reset();
		var result = matcher.find();
		if (result) {
			do {
				msg.delete(matcher.start(), matcher.end());
				msg.insert(matcher.start(), REPLACEMENT);
				result = matcher.find();
			} while (result);
		}
	}
}
