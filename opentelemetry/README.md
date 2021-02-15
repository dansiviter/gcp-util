# Cloud Operations Utils. - OpenTelemetry #

An implementation of OpenTelemetry that sends it's data to Cloud Trace or Cloud Monitoring.

Limitations:
* No performance testing,
* Currently no Metrics.

## Usage ##

```java
  Exporter exporter = Exporter.builder()
          .build();
  SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
          .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
          .build();
  OpenTelemetrySdk.builder()
          .setTracerProvider(tracerProvider)
          .buildAndRegisterGlobal();
```

> :information_source: avoid the `SimpleSpanProcessor` as this will force persistence when the span completes potentially slowing down processing. Use `BatchSpanProcessor` instead.
