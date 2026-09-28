package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.schedulers.CollectionProperties;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VendorCallLimiterTest {

	private final VendorCallLimiter limiter = new VendorCallLimiter(
			new CollectionProperties(true, 50, Duration.ofSeconds(60), 8, 1));

	@Test
	void shouldReturnTheCallResult_andReleaseThePermit_evenWhenTheCallFails() {
		assertThat(limiter.call(VendorCode.CISCO, () -> "ok")).isEqualTo("ok");
		assertThatThrownBy(() -> limiter.call(VendorCode.CISCO, () -> {
			throw new IllegalStateException("vendor down");
		})).hasMessage("vendor down");

		// the single permit was released: the next call still runs
		assertThat(limiter.call(VendorCode.CISCO, () -> "again")).isEqualTo("again");
	}

	@Test
	void shouldLimitEachVendorSeparately() throws Exception {
		// given: 1 permit per vendor; a Samsung call is holding Samsung's permit
		CountDownLatch samsungStarted = new CountDownLatch(1);
		CountDownLatch releaseSamsung = new CountDownLatch(1);
		AtomicInteger samsungCalls = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> limiter.call(VendorCode.SAMSUNG, () -> {
				samsungStarted.countDown();
				await(releaseSamsung);
				return samsungCalls.incrementAndGet();
			}));
			assertThat(samsungStarted.await(2, TimeUnit.SECONDS)).isTrue();

			// when / then: Amazon is not blocked by Samsung's permit
			assertThat(limiter.call(VendorCode.AMAZON, () -> "amazon")).isEqualTo("amazon");

			// and a second Samsung call waits until the first one finishes
			var second = pool.submit(() -> limiter.call(VendorCode.SAMSUNG, samsungCalls::incrementAndGet));
			Thread.sleep(100);
			assertThat(second.isDone()).isFalse();
			releaseSamsung.countDown();
			assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(2);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
