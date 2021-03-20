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
package uk.dansiviter.gcp.opentracing;

import static uk.dansiviter.juli.annotations.Message.Level.DEBUG;
import static uk.dansiviter.juli.annotations.Message.Level.WARN;

import com.google.api.gax.rpc.ApiException;

import uk.dansiviter.juli.annotations.Log;
import uk.dansiviter.juli.annotations.Message;

/**
 * Defines the logger.
 */
@Log
public interface Logger {
  @Message(value = "Flushing spans... [size={0}]", level = DEBUG)
  void flush(int size);

  @Message(value = "Unable to persist span!", level = WARN)
  void persistFail(ApiException e);
}
