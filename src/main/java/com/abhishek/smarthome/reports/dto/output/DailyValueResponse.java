package com.abhishek.smarthome.reports.dto.output;

import com.abhishek.smarthome.reports.entity.DailyValue;
import java.time.LocalDate;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** One day of a metric's breakdown: the total (counters, switch) or the average (gauges). */
@Getter
public final class DailyValueResponse {

	private final LocalDate date;
	private final @Nullable Double value;

	private DailyValueResponse(DailyValue dailyValue) {
		this.date = dailyValue.getLocalDate();
		this.value = dailyValue.getValue();
	}

	public static DailyValueResponse from(DailyValue dailyValue) {
		return new DailyValueResponse(dailyValue);
	}
}
