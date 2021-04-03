# Microprofile Metrics Cloud Monitoring Integration

Bridges the gap between Microprofile Metrics and Cloud Monitoring.

This implementation attempts the following:
1. Snapshots the data as fast as possible,
2. If not created already does it's best to create a `com.google.api.MetricDescriptor` for the `Metric` and persists to Cloud Monitoring,
3. Iterates around the snapshot data persisting it to Cloud Monitoring.

Metric will have the following values:
* Type: `custom.googleapis.com/microprofile/<registry_type>/<MetricId#name>`
* Display Name: `MetricId#displayName`
* Description: `Metadata#description`
* Unit: `Metadata#unit`
* Value Type: Detected from first sample of data

Limitations:
* Missing `org.eclipse.microprofile.metrics.Meter`,
* Performance testing - No idea of the runtime impact of this library underload.


> :information_source: Repeat quick start/stop of instances may result in a `INVALID_ARGUMENT: ... One or more points were written more frequently than the maximum sampling period configured for the metric.` error. This is especially true for local development as it will tend to use the `global` monitored resource type. This will not appear when running in GKE or GCE as the hostname has sufficient entropy to avoid conflicts.


## Usage ##

By simply adding this as a dependency is sufficient for it to start exporting metrics to Cloud Monitoring.


### Settings ###

| Key                            | Description                                | Value             | Default | Notes |
|--------------------------------|--------------------------------------------|-------------------|---------|-----|
| `cloudMonitoring.samplingRate` | The duration between collection of metrics | `STANDARD\|HIGH_RESOLUTION` | `STANDARD`  | For more info see [here](https://cloud.google.com/blog/products/management-tools/cloud-monitoring-metrics-get-10-second-resolution). |


### JAX-RS ###

The following metrics can be collected from JAX-RS for both Container and Client:
* `request.count`: The number of requests received with `path` tag,
* `response.count`: The number of requests received with `path` and `response_code` tags,
* `request.latency`: The time it took for the application code to process the request and respond with `path` and `response_code` tags.

To enable these use the following classes:
* `uk.dansiviter.gcp.microprofile.metrics.jaxrs.ContainerMetricsFeature`,
* `uk.dansiviter.gcp.microprofile.metrics.jaxrs.ClientMetricsFeature`.


## Dashboard ##

`dashboard.json` is an example Cloud Monitoring dashboard that shows some of the Microprofile metrics.
