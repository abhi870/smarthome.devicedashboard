package com.abhishek.smarthome.service;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.schedulers.CollectionProperties;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Caps the vendor calls in flight per vendor ({@code smarthome.collection.max-concurrent-per-vendor}), so parallel
 * collection never sends one vendor more concurrent requests than its rate limit tolerates, while other vendors'
 * calls proceed. One fair semaphore per vendor, created at startup and never replaced (thread-safe reads).
 */
@Component
public class VendorCallLimiter {

	private final Map<VendorCode, Semaphore> permitsByVendor = new EnumMap<>(VendorCode.class);

	public VendorCallLimiter(CollectionProperties collectionProperties) {
		for (VendorCode vendor : VendorCode.values()) {
			permitsByVendor.put(vendor, new Semaphore(collectionProperties.getMaxConcurrentPerVendor(), true));
		}
	}

	/**
	 * Runs {@code call} once a permit for {@code vendor} is free (blocking until then).
	 *
	 * @throws IllegalStateException if the thread is interrupted while waiting (e.g. on shutdown)
	 */
	public <T> T call(VendorCode vendor, Supplier<T> call) {
		Semaphore permits = permitsByVendor.get(vendor);
		try {
			permits.acquire();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting to call " + vendor, e);
		}
		try {
			return call.get();
		}
		finally {
			permits.release();
		}
	}
}
