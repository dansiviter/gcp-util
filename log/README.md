# Stackdriver Utils. - Logging #

## Configuration ##

### `java.util` Logger (JUL) ###

> :information_source: Don't place the exception within the message. This will be appended separately within `stack_trace` property and Error Reporting will use that. For JBoss Logging that could be as simple as `%s`.

#### File Config ####

Inspired by `com.google.cloud.logging.LoggingHandler` but one major limitation is it's use of `com.google.cloud.logging.Payload.StringPayload` which heavily limits the data that can be utilised by GCP. This implementation does not use that to give broader support to Stackdriver's features.

Example `java.util.logging.config.file` file config:

```
.level=INFO
handlers=uk.dansiviter.stackdriver.log.jul.JulHandler

uk.dansiviter.stackdriver.log.jul.JulHandler.level=INFO
uk.dansiviter.stackdriver.log.jul.JulHandler.uk.dansiviter.stackdriver.log.JulHandler.filter=foo.MyFilter
uk.dansiviter.stackdriver.log.jul.JulHandler.decorators=foo.MyDecorator
uk.dansiviter.stackdriver.log.jul.JulHandler.enhancers=io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer
```

#### Class Config ####

Example `java.util.logging.config.class` class config:

```java
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
```

### JBoss Logger ###

JBoss logger is based on the JUL logger, however has some extensions. Therefore, the configuration is largely identical however format is a little different. Example:

```
logger.level=INFO
logger.handlers=STACKDRIVER

handler.STACKDRIVER=uk.dansiviter.stackdriver.log.jul.JulHandler
handler.STACKDRIVER.level=INFO
handler.STACKDRIVER.properties=decorators,enhancers
handler.STACKDRIVER.decorators=foo.MyDecorator
handler.STACKDRIVER.enhancers=io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer
```

### Log4J v2 ###

For Log4J v2 it's highly recommended a Failover appender is used to ensure any potential configuration issues will pipe the logs to console. This aids debugging when there are issues:

```xml
<Configuration>
	<Appenders>
		<Console name="console" />
		<Stackdriver name="stackdriver">
			<Decorator class="foo.MyDecorator" />
			<Enhancer class="io.opencensus.contrib.logcorrelation.stackdriver.OpenCensusTraceLoggingEnhancer" />
		</Stackdriver>
		<Falover name="failover" primary="stackdriver">
			<Failovers>
				<AppenderRef ref="console" />
			</Failovers>
		</Failover>
	</Appenders>
	<Loggers>
		<Root level="info">
			<AppenderRef ref="failover" />
		</Root>
	</Loggers>
<Configuration>
```

## Decorators ##

### OpenTracing Log Correlation ###

To link traces to logs use `uk.dansiviter.stackdriver.log.opentracing.Decorator`.

### JBoss Logger ###

To embed MDC params into log use `uk.dansiviter.stackdriver.log.jboss.MdcDecorator`. Use with caution as this could easily lead to an information leak.
