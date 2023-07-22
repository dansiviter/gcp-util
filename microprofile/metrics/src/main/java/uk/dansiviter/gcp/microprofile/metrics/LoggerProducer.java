package uk.dansiviter.gcp.microprofile.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import uk.dansiviter.jule.LogProducer;

@ApplicationScoped
public class LoggerProducer {
  @Produces
  public static Logger myLog(InjectionPoint ip) {
    return LogProducer.log(Logger.class, ip.getMember().getDeclaringClass());
  }
}
