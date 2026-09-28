package com.abhishek.smarthome.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.enums.MetricType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricStatisticsCalculatorTest {

	private static final Instant T0 = Instant.parse("2026-09-27T00:00:00Z");
	private static final Duration GAP = Duration.ofMinutes(15);

	private static MetricSample at(int minute, double value) {
		return new MetricSample(T0.plusSeconds(minute * 60L), value);
	}

	private static Instant minute(int minute) {
		return T0.plusSeconds(minute * 60L);
	}

	@Test
	void shouldComputeGaugeStatistics_withoutTotal() {
		// when: one reading per minute over 3 minutes (unsorted input)
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.TEMPERATURE,
				List.of(at(2, 30), at(0, 10), at(1, 20)), null, T0, minute(3), GAP);

		// then
		assertThat(statistics.getSampleCount()).isEqualTo(3);
		assertThat(statistics.getMin()).isEqualTo(10.0);
		assertThat(statistics.getMax()).isEqualTo(30.0);
		assertThat(statistics.getAvg()).isEqualTo(20.0);
		assertThat(statistics.getFirstValue()).isEqualTo(10.0);
		assertThat(statistics.getLastTime()).isEqualTo(minute(2));
		assertThat(statistics.getTotal()).isNull();
		assertThat(statistics.getCoveragePct()).isEqualTo(100.0);
	}

	@Test
	void shouldWeightAverageByTimeEachValueHeld() {
		// when: 10 held for 2 minutes, 40 for 1 minute
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.POWER,
				List.of(at(0, 10), at(2, 40)), null, T0, minute(3), GAP);

		// then: (10×120 + 40×60) / 180 = 20, not the plain mean 25
		assertThat(statistics.getAvg()).isEqualTo(20.0);
	}

	@Test
	void shouldCountGapsLongerThanMaxGapAsMissing() {
		// when: two readings an hour apart in a 2-hour period, each counts for at most 15 minutes
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.TEMPERATURE,
				List.of(at(0, 10), at(60, 20)), null, T0, minute(120), GAP);

		// then
		assertThat(statistics.getCoveredSeconds()).isEqualTo(1800);
		assertThat(statistics.getCoveragePct()).isEqualTo(25.0);
		assertThat(statistics.getAvg()).isEqualTo(15.0);
	}

	@Test
	void shouldSumCounterIncreases_fromPreviousReading_andIgnoreResets() {
		// when: counter was 100 before the period, then 100.5, 101, reset to 0.2, 0.7
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.ENERGY,
				List.of(at(0, 100.5), at(1, 101), at(2, 0.2), at(3, 0.7)), new MetricSample(T0.minusSeconds(60), 100), T0,
				minute(4), GAP);

		// then: 0.5 + 0.5 + (reset) + 0.5
		assertThat(statistics.getTotal()).isEqualTo(1.5);
	}

	@Test
	void shouldCountCounterFromFirstReading_whenNoPreviousReading() {
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.ENERGY,
				List.of(at(0, 5), at(1, 7)), null, T0, minute(2), GAP);

		assertThat(statistics.getTotal()).isEqualTo(2.0);
	}

	@Test
	void shouldCountMinutesSwitchedOn() {
		// when: on 00:00–00:10, off 00:10–00:30, on 00:30–00:40
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.SWITCH,
				List.of(at(0, 1), at(10, 0), at(30, 1)), null, T0, minute(40), Duration.ofHours(1));

		// then
		assertThat(statistics.getTotal()).isEqualTo(20.0);
		assertThat(statistics.getHeadlineValue()).isEqualTo(20.0);
	}

	@Test
	void shouldReturnEmptyStatistics_whenNoReadings() {
		MetricStatistics statistics = MetricStatisticsCalculator.calculate(MetricType.HUMIDITY, List.of(), null, T0,
				minute(60), GAP);

		assertThat(statistics.getSampleCount()).isZero();
		assertThat(statistics.getAvg()).isNull();
		assertThat(statistics.getTotal()).isNull();
		assertThat(statistics.getPeriodSeconds()).isEqualTo(3600);
		assertThat(statistics.getCoveragePct()).isZero();
	}
}
