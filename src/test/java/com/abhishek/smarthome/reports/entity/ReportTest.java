package com.abhishek.smarthome.reports.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.enums.ReportStatus;
import com.abhishek.smarthome.reports.enums.ReportType;
import com.abhishek.smarthome.reports.service.MetricStatistics;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportTest {

	private static final Instant NOW = Instant.parse("2026-09-28T02:00:00Z");
	private static final UUID HOME_DEVICE_ID = UUID.randomUUID();
	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

	@Test
	void shouldCoverTheHomeLocalDay_whenDaily() {
		// when
		Report report = Report.daily(HOME_DEVICE_ID, KOLKATA, LocalDate.parse("2026-09-27"),
				Set.of(MetricType.TEMPERATURE, MetricType.ENERGY), NOW);

		// then: midnight to midnight in Kolkata (UTC+05:30)
		assertThat(report.getType()).isEqualTo(ReportType.DAILY);
		assertThat(report.getPeriodStart()).isEqualTo(Instant.parse("2026-09-26T18:30:00Z"));
		assertThat(report.getPeriodEnd()).isEqualTo(Instant.parse("2026-09-27T18:30:00Z"));
		assertThat(report.getTimezone()).isEqualTo("Asia/Kolkata");
		assertThat(report.getMetrics()).containsExactlyInAnyOrder(MetricType.TEMPERATURE, MetricType.ENERGY);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.QUEUED);
	}

	@Test
	void shouldHave23Hours_onDstStartDay() {
		Report report = Report.daily(HOME_DEVICE_ID, ZoneId.of("Europe/Berlin"), LocalDate.parse("2026-03-29"),
				Set.of(MetricType.POWER), NOW);

		assertThat(Duration.between(report.getPeriodStart(), report.getPeriodEnd())).isEqualTo(Duration.ofHours(23));
	}

	@Test
	void shouldStoreSummaries_andAverageCoverage_whenCompleted() {
		// given
		Report report = Report.customRange(HOME_DEVICE_ID, KOLKATA, NOW.minusSeconds(7200), NOW,
				Set.of(MetricType.TEMPERATURE, MetricType.ENERGY), NOW);
		MetricSummary full = MetricSummary.from(new MetricStatistics(MetricType.TEMPERATURE, 2, 1.0, 2.0, 1.5, 1.0, NOW,
				2.0, NOW, null, 7200, 7200));
		MetricSummary half = MetricSummary.from(new MetricStatistics(MetricType.ENERGY, 1, 5.0, 5.0, 5.0, 5.0, NOW, 5.0,
				NOW, 0.0, 3600, 7200));

		// when
		report.start();
		report.complete(List.of(full, half), List.of(DailyValue.of(MetricType.ENERGY, LocalDate.parse("2026-09-28"), 0.0)),
				NOW);

		// then
		assertThat(report.getStatus()).isEqualTo(ReportStatus.SUCCEEDED);
		assertThat(report.getAttempts()).isEqualTo(1);
		assertThat(report.getCoveragePct()).isEqualTo(75.0);
		assertThat(report.getSummaries()).extracting(MetricSummary::getUnit).containsExactly("C", "kWh");
		assertThat(report.getDailyValues()).hasSize(1);
		assertThat(report.getCompletedAt()).isEqualTo(NOW);
	}

	@Test
	void shouldRetryWithLinearBackoff_untilMaxAttempts() {
		// given
		Report report = Report.daily(HOME_DEVICE_ID, KOLKATA, LocalDate.parse("2026-09-27"), Set.of(MetricType.POWER), NOW);
		Duration delay = Duration.ofMinutes(30);

		// when / then: 1st failure → retry in 30 min
		report.start();
		report.fail("vendor down", NOW, delay, 2);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
		assertThat(report.getNextAttemptAt()).isEqualTo(NOW.plus(delay));
		assertThat(report.isRetryDue(NOW)).isFalse();
		assertThat(report.isRetryDue(NOW.plus(delay))).isTrue();

		// 2nd failure → attempts exhausted, no more automatic retries
		report.start();
		report.fail("vendor down", NOW, delay, 2);
		assertThat(report.getAttempts()).isEqualTo(2);
		assertThat(report.getNextAttemptAt()).isNull();
		assertThat(report.isRetryDue(NOW.plus(Duration.ofDays(1)))).isFalse();
	}
}
