# Microprofile Metrics Stackdriver Bridge

Bridges the gap between Microprofile Metrics and Stackdriver.

This implementation attempts the following:
1. Snapshots the data as fast as possible,
2. If not created already does it's best to create a `com.google.api.MetricDescriptor` for the `Metric` and persists to Stackdriver,
3. Iterates around the snapshot data persisting it to Stackdriver.

Metric will have the following values:
* Type: `custom.googleapis.com/microprofile/<registry_type>/<MetricId#name>`
* Display Name: `MetricId#displayName`
* Description: `Metadata#description`
* Unit: `Metadata#unit`
* Value Type: Detected from first sample of data

Limitations:
* Missing `org.eclipse.microprofile.metrics.Meter`,
* Performance testing - No idea of the runtime impact of this library underload.


## Usage ##

It needs a `java.util.concurrent.ScheduledExecutorService` to be able to run which not often given by default, so ensure one is supplied. e.g.:

	@ApplicationScoped
	public static class ExecutorProvider {
		@javax.enterprise.inject.Produces @ApplicationScoped
		public ScheduledExecutorService scheduler() {
			return Executors.newSingleThreadScheduledExecutor();
		}
	}


### Settings ###

| Key                        | Description                                | Value             | Default |
|----------------------------|--------------------------------------------|-------------------|---------|
| `stackdriver.pollDuration` | The duration between collection of metrics | ISO-8601 Duration | `PT1M`  |


### JAX-RS ###

The following metrics can be collected from JAX-RS for both Container and Client:
* `request.count`: The number of requests received with `path` tag,
* `response.count`: The number of requests received with `path` and `response_code` tags,
* `request.latency`: The time it took for the application code to process the request and respond with `path` and `response_code` tags.

To enable these use the following classes:
* `uk.dansiviter.stackdriver.microprofile.metrics.jaxrs.ContainerMetricsFeature`,
* `uk.dansiviter.stackdriver.microprofile.metrics.jaxrs.ClientMetricsFeature`.


## Dashboard ##

`dashboard.json` is an example Stackdriver dashboard that shows some of the Microprofile metrics.
