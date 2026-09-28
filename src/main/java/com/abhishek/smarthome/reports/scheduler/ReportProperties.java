package com.abhishek.smarthome.reports.scheduler;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** {@code smarthome.reports.*}: daily report job and on-demand report limits. */
@Validated
@ConfigurationProperties("smarthome.reports")
@Getter
public final class ReportProperties {

	/** Turns the daily report job on or off (on-demand reports always work). */
	private final boolean enabled;

	/** How long after a home's local midnight the previous day is reported, so late readings are in. */
	private final Duration gracePeriod;

	/** How many past days the job catches up on (e.g. after downtime); older missing days are not generated. */
	@Min(1)
	@Max(366)
	private final int backfillDays;

	/** Most device-days generated per tick. */
	@Min(1)
	@Max(10_000)
	private final int batchSize;

	/** Wait before retrying a failed DAILY report, multiplied by the attempt number. */
	private final Duration retryDelay;

	/** Attempts per DAILY report before the job gives up (a regenerate request still works). */
	@Min(1)
	@Max(20)
	private final int maxAttempts;

	/** A reading counts for at most this long; longer gaps count as missing data (coverage). */
	private final Duration maxSampleGap;

	/** Longest range an on-demand report may cover. */
	@Min(1)
	@Max(3660)
	private final int maxRangeDays;

	/** Device-days the daily job generates at the same time (threads of the report pool; each uses a DB connection). */
	@Min(1)
	@Max(32)
	private final int parallelism;

	public ReportProperties(@DefaultValue("true") boolean enabled, @DefaultValue("1h") Duration gracePeriod,
			@DefaultValue("7") int backfillDays, @DefaultValue("500") int batchSize,
			@DefaultValue("30m") Duration retryDelay, @DefaultValue("5") int maxAttempts,
			@DefaultValue("15m") Duration maxSampleGap, @DefaultValue("366") int maxRangeDays,
			@DefaultValue("4") int parallelism) {
		this.enabled = enabled;
		this.gracePeriod = gracePeriod;
		this.backfillDays = backfillDays;
		this.batchSize = batchSize;
		this.retryDelay = retryDelay;
		this.maxAttempts = maxAttempts;
		this.maxSampleGap = maxSampleGap;
		this.maxRangeDays = maxRangeDays;
		this.parallelism = parallelism;
	}
}
