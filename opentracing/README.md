# Stackdriver Utils. - OpenTracing #

An implementation of `io.opentracing.Tracer` that sends it's data to Stackdriver.

Limitations:
* No performance testing.

## Usage ##

Some helpers to get you started.

### Helidon ###

Implement `io.helidon.tracing.spi.TracerProvider` and a matching `ServiceLoader` file:

	public class StackdriverTracerProvider implements TracerProvider {

		@Override
		public TracerBuilder<?> createBuilder() {
			return new Builder();
		}

		private static class Builder implements TracerBuilder<Builder> {
			... implement other methods as no-op.

			@Override
			public Tracer build() {
				final Tracer tracer = StackdriverTracer.builder().sampler(Sampler.always()).build();
				GlobalTracer.register(tracer);  // it doesn't do this :(
				return tracer;
			}
		}
	}

It may also be important to create a `io.opentracing.ScopeManager` instance to ensure context is spanned across executors. See [Integration Test](../integration-test) project for an example.

### Thorntail ###

Thorntail uses (Tracer Resolver)[https://github.com/opentracing-contrib/java-tracerresolver/blob/master/opentracing-tracerresolver] to simplify this somewhat.
