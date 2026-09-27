package com.abhishek.smarthome.schedulers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** {@code smarthome.collection.*}: metrics collection scheduler settings. */
@Validated
@ConfigurationProperties("smarthome.collection")
public final class CollectionProperties {

	/** Turns the scheduler on or off (e.g. off in some tests or on a read-only replica). */
	private final boolean enabled;

	/** Most devices collected per tick. */
	@Min(1)
	@Max(1000)
	private final int batchSize;

	/** Wait before retrying a device whose collection failed (capped at its polling interval). */
	private final Duration retryDelay;

	public CollectionProperties(@DefaultValue("true") boolean enabled, @DefaultValue("50") int batchSize,
			@DefaultValue("60s") Duration retryDelay) {
		this.enabled = enabled;
		this.batchSize = batchSize;
		this.retryDelay = retryDelay;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public Duration getRetryDelay() {
		return retryDelay;
	}
}
