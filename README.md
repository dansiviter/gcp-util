[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/dansiviter/gcp-util/Build?style=flat-square)](https://github.com/dansiviter/gcp-util/actions/workflows/build.yaml) [![Known Vulnerabilities](https://snyk.io/test/github/dansiviter/gcp-util/badge.svg?style=flat-square)](https://snyk.io/test/github/dansiviter/gcp-util) [![Sonar Coverage](https://img.shields.io/sonar/coverage/dansiviter_gcp-util?server=https%3A%2F%2Fsonarcloud.io&style=flat-square)](https://sonarcloud.io/dashboard?id=dansiviter_gcp-util) ![Java 11+](https://img.shields.io/badge/-Java%2011%2B-informational?style=flat-square)

# Google Cloud Platform Utils. #

A collection of utilities and experiments for integrating into Google Cloud Platform:

* [`Core`](/core) - Core classes common to multiple projects,
* [`JDBC Commenter`](/jdbccommenter) - [SQL Commenter](https://google.github.io/sqlcommenter/) implementation for JDBC,
* [`Log`](/log) - A slightly more advanced Cloud Logging integration implementation,
* [`Microprofile Config`](/microprofile/config) - Integration for Google Secrets Manager within config,
* [`Microprofile Metrics`](/microprofile/metrics) - Extracts and sends Microprofile Metrics to Cloud Monitoring,
* [`OpenTelemetry`](/opentelemetry) - [OpenTelemetry](https://opentelemetry.io) implementation that sends trace and metrics information to Cloud Tracing.

> :information_source: OpenTracing implementation has been replaced by a combination of the [OpenTelemetry tracer](/opentelemetry) exporter and [OpenTracing shim](https://github.com/open-telemetry/opentelemetry-java/tree/main/opentracing-shim).

> :warning: These utilities are not suitable for production environments. They are simply experiments.


## Running Locally ##

Ensure `gcloud auth application-default login` is run to set the default credentials.


## `MonitoredResource` Resolution ##

By default auto-detection will be attempted via `uk.dansiviter.gcp.MonitoredResourceProvider#monitoredResource()`. However, if you wish to override this you can use the `java.util.ServiceLoader` mechanism via `MonitoredResourceProvider`.


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
