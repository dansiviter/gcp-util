# Stackdriver Utils. #

A collection of utilities and experiments for integrating into stackdriver:

* [`core`](/core) - Core classes common to multiple projects,
* [`log`](/log) - A slightly more modern Stackdriver Logging integration implementation,
* [`microprofile/metrics`](/microprofile/metrics) - Extracts and sends Microprofile Metrics to Stackdriver,
* [`opentracing`](/opentracing) - Sends trace information to Stackdriver. Also useful for Microprofile Trace.


## Running Locally ##

Ensure `gcloud auth application-default login` is run to set the default credentials.
