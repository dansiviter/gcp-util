# Microprofile Metrics Stackdriver Bridge

Bridges the gap between Microprofile Metrics and Stackdriver.

This implementation attempts the following:
1. Snapshots the data as fast as possible,
2. If not created already does it's best to create a `com.google.api.MetricDescriptor` for the `Metric` and persists to Stackdriver,
3. Iterates around the snapshot data persisting it to Stackdriver.

Limitations:
* Zero unit testing... sorry!
* Do we need to attempt extract an existing `MetricDescriptor` or is it idempotent to just re-persist?
* Missing `org.eclipse.microprofile.metrics.Meter`,
* Missing `org.eclipse.microprofile.metrics.Histogram`,
* Missing `org.eclipse.microprofile.metrics.Timer`,
* Performance testing.
