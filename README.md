# Google Cloud Platform Utils. #

A collection of utilities and experiments for integrating into Google Cloud Platform:

* [`core`](/core) - Core classes common to multiple projects,
* [`log`](/log) - A slightly more modern Cloud Logging integration implementation,
* [`microprofile/config`](/microprofile/config) - Integration for Google Secrets Manager within config,
* [`microprofile/metrics`](/microprofile/metrics) - Extracts and sends Microprofile Metrics to Cloud Monitoring,
* [`opentelemetry`](/opentelemetry) - [OpenTelemetry](https://opentelemetry.io) implementation that sends trace and metrics informtaion to Cloud Tracing.
* [`opentracing`](/opentracing) - [OpenTracing](https://opentracing.io) implementation that sends trace information to Cloud Tracing. Also useful for Microprofile Trace.

> :warning: These utilities are not suitable for production environments. They are simply experiments.

## Running Locally ##

Ensure `gcloud auth application-default login` is run to set the default credentials.
