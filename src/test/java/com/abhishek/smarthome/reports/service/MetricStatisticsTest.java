package com.abhishek.smarthome.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.enums.MetricType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricStatisticsTest {

	private static final Instant DAY1 = Instant.parse("2026-09-26T00:00:00Z");
	private static final Instant DAY2 = Instant.parse("2026-09-27T00:00:00Z");

	@Test
	void shouldCombineDays_weightingAveragesByCoveredTime() {
		// given: day 1 avg 10 over 100 s, day 2 avg 20 over 300 s
		MetricStatistics day1 = new MetricStatistics(MetricType.TEMPERATURE, 10, 5.0, 15.0, 10.0, 5.0, DAY1, 15.0,
				DAY1.plusSeconds(90), null, 100, 86_400);
		MetricStatistics day2 = new MetricStatistics(MetricType.TEMPERATURE, 30, 12.0, 25.0, 20.0, 12.0, DAY2, 25.0,
				DAY2.plusSeconds(290), null, 300, 86_400);

		// when
		MetricStatistics combined = MetricStatistics.combine(MetricType.TEMPERATURE, List.of(day2, day1));

		// then
		assertThat(combined.getSampleCount()).isEqualTo(40);
		assertThat(combined.getMin()).isEqualTo(5.0);
		assertThat(combined.getMax()).isEqualTo(25.0);
		assertThat(combined.getAvg()).isEqualTo(17.5);
		assertThat(combined.getFirstTime()).isEqualTo(DAY1);
		assertThat(combined.getLastValue()).isEqualTo(25.0);
		assertThat(combined.getCoveredSeconds()).isEqualTo(400);
		assertThat(combined.getPeriodSeconds()).isEqualTo(172_800);
		assertThat(combined.getTotal()).isNull();
	}

	@Test
	void shouldAddTotals_andIgnoreDaysWithoutData() {
		// given
		MetricStatistics day1 = new MetricStatistics(MetricType.ENERGY, 1440, 100.0, 101.2, 100.6, 100.0, DAY1, 101.2,
				DAY1.plusSeconds(86_340), 1.25, 86_400, 86_400);
		MetricStatistics day2 = MetricStatistics.empty(MetricType.ENERGY, 86_400);

		// when
		MetricStatistics combined = MetricStatistics.combine(MetricType.ENERGY, List.of(day1, day2));

		// then
		assertThat(combined.getTotal()).isEqualTo(1.25);
		assertThat(combined.getCoveragePct()).isEqualTo(50.0);
		assertThat(combined.getHeadlineValue()).isEqualTo(1.25);
	}
}
