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


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import com.google.api.gax.core.BackgroundResource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link GaxUtil}.
 */
@ExtendWith(MockitoExtension.class)
public class GaxUtilTest {
	@Test
	public void close(@Mock BackgroundResource resource) throws Exception {
		when(resource.awaitTermination(anyLong(), any())).thenReturn(true);

		GaxUtil.close(resource);

		verify(resource).shutdown();
		verify(resource).awaitTermination(5, TimeUnit.SECONDS);
		verify(resource).close();
		assertThat(Thread.interrupted(), is(false));

		verifyNoMoreInteractions(resource);
	}

	@Test
	public void close_timeout(@Mock BackgroundResource resource) throws Exception {
		GaxUtil.close(resource);

		verify(resource).shutdown();
		verify(resource).awaitTermination(5, TimeUnit.SECONDS);
		verify(resource).shutdownNow();
		verify(resource).close();
		assertThat(Thread.interrupted(), is(false));

		verifyNoMoreInteractions(resource);
	}

	@Test
	public void close_throwInterrupt(@Mock BackgroundResource resource) throws Exception {
		when(resource.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException());

		GaxUtil.close(resource);

		verify(resource).shutdown();
		verify(resource).awaitTermination(5, TimeUnit.SECONDS);
		verify(resource).shutdownNow();
		verify(resource).close();
		assertThat(Thread.interrupted(), is(true));

		verifyNoMoreInteractions(resource);
	}
}
