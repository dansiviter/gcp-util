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
package uk.dansiviter.gcp.monitoring.opentracing.propagation;

/**
 * @author Daniel Siviter
 * @since v1.0 [16 Dec 2019]
 */
public class W3cTraceStatePropagator extends B3SinglePropagator {
	private final String TRACE_STATE = "tracestate";

	@Override
	protected String header() {
		return TRACE_STATE;
	}
}