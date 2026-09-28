package com.abhishek.smarthome.reports.entity;

import com.abhishek.smarthome.enums.MetricType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * One point of a CUSTOM_RANGE report's daily breakdown (table {@code report_daily_value}): a metric's headline value
 * on one home-local day — the total for counters and switches, the average for gauges.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class DailyValue {

	@Enumerated(EnumType.STRING)
	@Column(name = "metric", nullable = false, length = 30)
	private MetricType metric;

	@Column(name = "local_date", nullable = false)
	private LocalDate localDate;

	@Column(name = "daily_value")
	private @Nullable Double value;

	public static DailyValue of(MetricType metric, LocalDate localDate, @Nullable Double value) {
		DailyValue dailyValue = new DailyValue();
		dailyValue.metric = metric;
		dailyValue.localDate = localDate;
		dailyValue.value = value;
		return dailyValue;
	}
}
