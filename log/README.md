# Stackdriver Utils. - Logging #

## `java.util` Logger Usage ##

> :information_source: Don't place the exception within the message. This will be appended separately within `stack_trace` property and Error Reporting will use that. For JBoss Logging that could be as simple as `%s`.

### File Config ###

Inspired by `com.google.cloud.logging.LoggingHandler` but one major limitation is it's use of `com.google.cloud.logging.Payload.StringPayload` which heavily limits the data that can be utilised by GCP.

Example `java.util.logging.config.file` file config:

	.level=INFO
	handlers=uk.dansiviter.stackdriver.log.JulHandler

	uk.dansiviter.stackdriver.log.JulHandler.level=INFO
	uk.dansiviter.stackdriver.log.JulHandler.uk.dansiviter.stackdriver.log.JulHandler.filter=foo.MyFilter
	uk.dansiviter.stackdriver.log.JulHandler.decorators=foo.MyDecorator
	uk.dansiviter.stackdriver.log.JulHandler.legacyEnhancers=io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer

### Class Config ###

Example `java.util.logging.config.class` class config:

	public class MyConfig {
      public MyConfig() {
        final JulHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        handler.setFilter(new MyFilter());
        handler.add(new MyDecorator()).add(new OpenCensusLoggingEnhancer());

        final Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        root.addHandler(consoleHandler);
      }
  	}

## Decorators ##

### OpenTracing Log Correlation ###

To link traces to logs use `uk.dansiviter.stackdriver.log.opentracing.Decorator`.

### JBoss Logger ###

To embed MDC params into log use `uk.dansiviter.stackdriver.log.jboss.MdcDecorator`.
