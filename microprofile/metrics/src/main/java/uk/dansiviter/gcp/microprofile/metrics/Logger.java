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

import static uk.dansiviter.juli.annotations.Message.Level.DEBUG;
import static uk.dansiviter.juli.annotations.Message.Level.WARN;

import java.time.Instant;

import uk.dansiviter.juli.annotations.Log;
import uk.dansiviter.juli.annotations.Message;

/**
 * Defines the logger.
 */
@Log
public interface Logger {
  @Message(value = "Starting metrics collection... [start={0},end={1}]", level = DEBUG)
  void startCollection(Instant start, Instant end);

  @Message(value = "Persisting time series. [size={0}]", level = DEBUG)
  void persist(int size);

  @Message(value = "Metric collection failure!", level = WARN)
  void collectionFail(RuntimeException e);

  @Message(value = "Snapshot failure!", level = WARN)
  void snapshotFail(RuntimeException e);

	@Message(value = "Unable to find projectId! Unable to export metrics.", level = WARN)
  void projectIdNotFound();
}
