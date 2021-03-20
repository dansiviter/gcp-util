# Cloud Operations Utils. - OpenTelemetry #

An implementation of OpenTelemetry that sends it's data to Cloud Trace or Cloud Monitoring.

Limitations:
* No performance testing,
* No Metrics implementation.

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

### OpenTracing Shim ###

If you're still limited to using OpenTracing, you can use the [Shim](https://github.com/open-telemetry/opentelemetry-java/tree/main/opentracing-shim) to link between the two.
