package com.abhishek.smarthome.schedulers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** {@code smarthome.collection.*}: metrics collection scheduler settings. */
@Validated
@ConfigurationProperties("smarthome.collection")
@Getter
public final class CollectionProperties {

	/** Turns the scheduler on or off (e.g. off in some tests or on a read-only replica). */
	private final boolean enabled;

	/** Most devices collected per tick. */
	@Min(1)
	@Max(1000)
	private final int batchSize;

	/** Wait before retrying a device whose collection failed (capped at its polling interval). */
	private final Duration retryDelay;

	/** Devices collected at the same time (threads of the collection pool). */
	@Min(1)
	@Max(64)
	private final int parallelism;

	/** Most calls in flight to one vendor at a time, whatever the parallelism (vendor rate limits). */
	@Min(1)
	@Max(64)
	private final int maxConcurrentPerVendor;

	public CollectionProperties(@DefaultValue("true") boolean enabled, @DefaultValue("50") int batchSize,
			@DefaultValue("60s") Duration retryDelay, @DefaultValue("8") int parallelism,
			@DefaultValue("4") int maxConcurrentPerVendor) {
		this.enabled = enabled;
		this.batchSize = batchSize;
		this.retryDelay = retryDelay;
		this.parallelism = parallelism;
		this.maxConcurrentPerVendor = maxConcurrentPerVendor;
	}
}
