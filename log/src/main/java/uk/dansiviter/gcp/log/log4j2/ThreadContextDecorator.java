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

import static uk.dansiviter.gcp.log.EntryDecorator.mdc;

import java.util.Map;

import com.google.cloud.logging.LogEntry.Builder;

import org.apache.logging.log4j.ThreadContext;

import uk.dansiviter.gcp.log.Entry;
import uk.dansiviter.gcp.log.EntryDecorator;

/**
 * Log4j2 {@link ThreadContext}. For consistency this will use the tag name {@code mdc}.
 *
 * @author Daniel Siviter
 * @since v1.0 [30 Oct 2020]
 */
public class ThreadContextDecorator implements EntryDecorator {
	private static final EntryDecorator DELEGATE = mdc(ThreadContext::getContext);

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		DELEGATE.decorate(b, e, payload);
	}
}
