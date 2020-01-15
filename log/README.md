# Stackdriver Utils. - Logging #

## `java.util` Logger Usage ##

### File Config ###

Inspired by `com.google.cloud.logging.LoggingHandler` but one major limitation is it's use of `com.google.cloud.logging.Payload.StringPayload` which heavily limits the data that can be utilised by GCP.

Example `java.util.logging.config.file` file config:

	.level=INFO
	handlers=uk.dansiviter.stackdriver.log.JulHandler

	uk.dansiviter.stackdriver.log.JulHandler.level=FINEST
	uk.dansiviter.stackdriver.log.JulHandler.formatter=java.util.logging.SimpleFormatter
	uk.dansiviter.stackdriver.log.JulHandler.filter=foo.MyFilter
	uk.dansiviter.stackdriver.log.JulHandler.decorators=foo.MyDecorator
	uk.dansiviter.stackdriver.log.JulHandler.legacyEnhancers=io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer

 	java.util.logging.SimpleFormatter.format=%3$s: %5$s%6$s

### Class Config ###

Example `java.util.logging.config.class` class config:

	public class MyConfig {
      public MyConfig() {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%3$s: %5$s%6$s");

        final JulHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        handler.setFormatter(new SimpleFormatter());
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
