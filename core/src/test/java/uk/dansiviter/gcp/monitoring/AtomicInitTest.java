package uk.dansiviter.gcp.monitoring;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link AtomicInit};
 */
public class AtomicInitTest {
	@Test
	public void get() {
		var init = AtomicInit.atomic(() -> "Hello!");

		assertEquals("Hello!", init.get());
	}

	@Test
	public void get_null() {
		var init = AtomicInit.atomic(() -> null);

		assertThrows(IllegalStateException.class, () -> init.get());
	}

	@Test
	public void get_multi() {
		var init = AtomicInit.atomic(() -> {
			sleep(100);
			return "Hello!";
		});

		var ref = new AtomicReference<String>();

		new Thread(() -> ref.set(init.get())).start();
		Awaitility.await().until(() -> "Hello!".equals(ref.get()));
		assertEquals("Hello!", init.get());
	}

	@Test
	public void get_multi_null() {
		var init = AtomicInit.atomic(() -> {
			sleep(100);
			return null;
		});

		var ref = new AtomicReference<Object>();

		new Thread(() -> ref.set(init.get())).start();

		assertThrows(IllegalStateException.class, () -> await().until(() -> "Hello!".equals(ref.get())));
		assertThrows(IllegalStateException.class, () -> init.get());
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
