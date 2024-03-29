/*
 * Copyright 2019-2021 Daniel Siviter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.dansiviter.gcp;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link AtomicInit};
 */
class AtomicInitTest {
	@Test
	void get() {
		var init = AtomicInit.atomic(() -> "Hello!");

		assertEquals("Hello!", init.get());
	}

	@Test
	void get_null() {
		var init = AtomicInit.atomic(() -> null);

		assertThrows(IllegalStateException.class, () -> init.get());
	}

	@Test
	void get_multi() {
		var init = AtomicInit.atomic(() -> {
			sleep(50);
			return "Hello!";
		});

		var future = CompletableFuture.supplyAsync(() -> init.get());

		await().until(() -> "Hello!".equals(future.get()));
		assertEquals("Hello!", init.get());
	}

	@Test
	void get_multi_null() {
		var init = AtomicInit.atomic(() -> {
			sleep(50);
			return null;
		});

		var future = CompletableFuture.supplyAsync(() -> init.get());

		await().until(() -> future.isDone());
		var ise0 = assertThrows(ExecutionException.class, () -> future.get());
		assertEquals("Instance supplied is null!", ise0.getCause().getMessage());
		var ise1 = assertThrows(IllegalStateException.class, () -> init.get());
		assertEquals("Initialiser closed!", ise1.getMessage());
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
