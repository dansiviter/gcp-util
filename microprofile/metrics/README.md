# Microprofile Metrics Stackdriver Bridge

Bridges the gap between Microprofile Metrics and Stackdriver.

This implementation attempts the following:
1. Snapshots the data as fast as possible,
2. If not created already does it's best to create a `com.google.api.MetricDescriptor` for the `Metric` and persists to Stackdriver,
3. Iterates around the snapshot data persisting it to Stackdriver.

Limitations:
* Zero unit testing... sorry!
* Missing `org.eclipse.microprofile.metrics.Meter`,
* Missing `org.eclipse.microprofile.metrics.Histogram`,
* Performance testing.


## Usage ##

It needs a `java.util.concurrent.ScheduledExecutorService` to be able to run which not often given by default, so ensure one is supplied. e.g.:

	@ApplicationScoped
	public static class ExecutorProvider {
		@javax.enterprise.inject.Produces @ApplicationScoped
		public ScheduledExecutorService scheduler() {
			return Executors.newSingleThreadScheduledExecutor();
		}
	}
