# Cloud Monitoring Logging Utils #

## Configuration ##

### `java.util` Logger (JUL) ###

This has two options:
* `uk.dansiviter.gcp.log.jul.JulHandler`: This will communicate directly with the APIs. This means a service account must be present in the VM or Pod with sufficient permissions,
* `uk.dansiviter.gcp.log.jul.JsonFormatter`: This will format log messages as required by [Structured Logging](https://cloud.google.com/logging/docs/structured-logging). This is often simpler than direct API access.

Both of these were inspired by `com.google.cloud.logging.LoggingHandler` but written to address it's limitation on the use of `com.google.cloud.logging.Payload.StringPayload` which heavily reduces the data that can be utilised by GCP for elements such as `serviceContext`.

> :information_source: The `java.util.logging.Formatter` for `uk.dansiviter.gcp.log.jul.JulHandler` is fixed as an contexual information is already provided. If you wish to add more information use `uk.dansiviter.gcp.log.EntryDecorator`.

#### `uk.dansiviter.gcp.log.jul.JulHandler` File Config ####

Example `java.util.logging.config.file` file config:

```
.level=INFO
handlers=uk.dansiviter.gcp.log.jul.JulHandler

uk.dansiviter.gcp.log.jul.JulHandler.level=INFO
uk.dansiviter.gcp.log.jul.JulHandler.filter=foo.MyFilter
uk.dansiviter.gcp.log.jul.JulHandler.decorators=uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator,foo.MyDecorator
```

> :information_source: It's highly recommended you use this in combination with `uk.dansiviter.juli.FallbackHandler`. As Cloud Logging is remote, in the unlikely event `JulHandler` cannot start the log record will be sent to the fallback `Handler`.

#### `uk.dansiviter.gcp.log.jul.JulHandler` Class Config ####

Example `java.util.logging.config.class` class config:

```java
public class MyConfig {
  public MyConfig() {
    final JulHandler handler = new ConsoleHandler();
    handler.setLevel(Level.INFO);
    handler.setFilter(new MyFilter());
    handler.add(new Decorator()).add(new MyDecorator());

    final Logger root = Logger.getLogger("");
    root.setLevel(Level.INFO);
    root.addHandler(consoleHandler);
  }
}
```

#### `uk.dansiviter.gcp.log.jul.JsonFormatter` File Config ####

```
.level=INFO
handlers=uk.dansiviter.juli.AsyncConsoleHandler

uk.dansiviter.juli.AsyncConsoleHandler.level=FINEST
uk.dansiviter.juli.AsyncConsoleHandler.formatter=uk.dansiviter.gcp.log.jul.JsonFormatter

uk.dansiviter.gcp.log.jul.JsonFormatter.decorators=uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator
```

### JBoss Logger ###

JBoss logger is based on the JUL logger, however has some extensions. Therefore, the configuration is largely identical however format is a little different. Example:

```
logger.level=INFO
logger.handlers=CLOUD_LOG

handler.CLOUD_LOG=uk.dansiviter.gcp.log.jul.JulHandler
handler.CLOUD_LOG.level=INFO
handler.CLOUD_LOG.properties=decorators
handler.CLOUD_LOG.decorators=uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator,foo.MyDecorator
```

### Log4J v2 ###

For Log4J v2 it's highly recommended a Failover appender is used to ensure any potential configuration issues will pipe the logs to console. This aids debugging when there are issues:

```xml
<Configuration>
  <Appenders>
    <CloudLogging name="java.log" synchronicity="ASYNC">
      <Decorators>
        <Decorator class="uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator"/>
        <Decorator class="foo.MyDecorator"/>
      </Decorators>
    </CloudLogging>
    <Falover name="failover" primary="cloudLogging">
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

### Logback ###

```xml
<configuration>
   <appender name="GCP" class="uk.dansiviter.gcp.log.logback.LogbackAppender">
     <logName>java.log</logName>
     <synchronicity>ASYNC</synchronicity>
     <decorators>uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator,foo.MyDecorator</decorators>
   </appender>
    <root level="DEBUG">
     <appender-ref ref="GCP" />
   </root>
 </configuration>
```

## Decorators ##

Both `com.google.cloud.logging.LoggingEnhancers` and the more flexible `uk.dansiviter.gcp.log.EntryDecorator` are supported in `decorators`. As this implementation uses ` com.google.cloud.logging.Payload.JsonPayload` the `EntryDecorator` gives more control over that.

If you require a little more flexibility in decorating, it's recommended you group these into a single class which has the added benefit of less verbosity in the configuration:

```java
public class MyCombinedDecorator implements EntryDecorator {
	private static final EntryDecorator DELEGATES = EntryDecorator.all(
		EntryDecorator.serviceContext(MyClass.class),
		new ThreadContextDecorator()
	);

	@Override
	public void decorate(Builder b, Entry e, Map<String, Object> payload) {
		DELEGATES.decorate(b, e, payload);
		...
	}
}
```

A few common use-cases are already implemented:

* `java.util.ServiceLoader` loading: `uk.dansiviter.gcp.log.ServiceLoaderDecorator`,
* Message masking: `uk.dansiviter.gcp.log.MessageMaskingDecorator` - may help with your DLP requirements,
* OpenTelemetry Log Correlation: `uk.dansiviter.gcp.log.OpenTelemetryTraceDecorator`,
* JBoss Logger MDC: `uk.dansiviter.gcp.log.jboss.MdcDecorator` - Use with caution! May lead to an information leak,
* Log4j v2 `ThreadContext`: `uk.dansiviter.gcp.log.log4j2.ThreadContextDecorator` - Use with caution! May lead to an information leak.

Check the JavaDoc for each class for more information.


## Limitations

* Timestamp Precision: Although the underlying protobuf `LogEntry` supports it, if using API client implementations, the maximum precision for timestamp is milliseconds. If you wish to have micro or nano then you must use a JSON formatter and Structured Logging. [googleapis/java-logging#598]
