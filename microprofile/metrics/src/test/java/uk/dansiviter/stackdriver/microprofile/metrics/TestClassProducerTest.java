
package uk.dansiviter.stackdriver.microprofile.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

interface Bar {
  String ping();
}

class Foo {
  @Inject
  Bar bar;

  String ping() {
    return bar.ping();
  }
}

@ExtendWith(WeldJunit5Extension.class)
class TestClassProducerTest {

	@WeldSetup
	public WeldInitiator weld = WeldInitiator.from(Foo.class).build();

	@ApplicationScoped
	@Produces
	Bar produceBar() {
	// Mock object provided by Mockito
	return Mockito.when(Mockito.mock(Bar.class).ping()).thenReturn("pong").getMock();
	}

	@Test
	public void testFoo() {
		Assertions.assertEquals("pong", weld.select(Foo.class).get().ping());
	}
}
