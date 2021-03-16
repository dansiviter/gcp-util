![GitHub Workflow Status](https://img.shields.io/github/workflow/status/dansiviter/gcp-util/Java%20CI?style=flat-square) [![Known Vulnerabilities](https://snyk.io/test/github/dansiviter/gcp-util/badge.svg?style=flat-square)](https://snyk.io/test/github/dansiviter/gcp-util) ![Java 11+](https://img.shields.io/badge/-Java%2011%2B-informational?style=flat-square)

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


## Java Platform Module System ##

gRPC has some issues when it comes to JPMS as both context, api and core share packages. Fortunately, Helidon have helped workaround this with `io.helidon.grpc:io.grpc` which means excluding the others:

```xml
<dependency>
  <groupId>uk.dansiviter.gcp</groupId>
  <artifactId>log</artifactId>
  <version>x.x.x</version>
  <exclusions>
    <exclusion>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-api</artifactId>
    </exclusion>
    <exclusion>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-core</artifactId>
    </exclusion>
    <exclusion>
      <groupId>io.grpc</groupId>
      <artifactId>grpc-context</artifactId>
    </exclusion>
  </exclusions>
</dependency>
<dependency>
  <groupId>io.helidon.grpc</groupId>
  <artifactId>io.grpc</artifactId>
</dependency>
```

If you see issues such as `java.io.IOException: Received fatal alert: handshake_failure` this is due to a lack of elliptical curve functionality which requires the `jdk.crypto.ec` module.
