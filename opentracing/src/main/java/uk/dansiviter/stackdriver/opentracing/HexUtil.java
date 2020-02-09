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
package uk.dansiviter.stackdriver.opentracing;

import java.nio.CharBuffer;
import java.util.OptionalLong;

/**
 *
 * @author Daniel Siviter
 * @since v1.0 [15 Dec 2019]
 */
public enum HexUtil { ;
	private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	/**
	 *
	 * @param high
	 * @param low
	 * @return
	 */
	public static String toHex(OptionalLong high, long low) {
		final CharBuffer buf = CharBuffer.allocate(high.isPresent() ? 32 : 16);
		high.ifPresent(h -> hex(buf, h));
		hex(buf, low);
		buf.flip();
		return buf.toString();
	}

	/**
	 *
	 * @param v
	 * @return
	 */
	public static String toHex(long v) {
		return toHex(OptionalLong.empty(), v);
	}

	/**
	 *
	 * @param buf
	 * @param v
	 */
	private static void hex(CharBuffer buf, long v) {
		hex(buf, (byte) ((v >>> 56L) & 0xff));
		hex(buf, (byte) ((v >>> 48L) & 0xff));
		hex(buf, (byte) ((v >>> 40L) & 0xff));
		hex(buf, (byte) ((v >>> 32L) & 0xff));
		hex(buf, (byte) ((v >>> 24L) & 0xff));
		hex(buf, (byte) ((v >>> 16L) & 0xff));
		hex(buf, (byte) ((v >>> 8L) & 0xff));
		hex(buf, (byte) (v & 0xff));
	}

	private static void hex(CharBuffer buf, byte b) {
		buf.put(HEX_DIGITS[(b >> 4) & 0xf]).put(HEX_DIGITS[b & 0xf]);
	}
}
