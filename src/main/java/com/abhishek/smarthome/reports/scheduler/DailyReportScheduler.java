package com.abhishek.smarthome.reports.scheduler;

import com.abhishek.smarthome.reports.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every {@code smarthome.reports.tick} (default 15 min) generates the DAILY reports that are due: for each home, the
 * local days that ended at least the grace period ago and have no report yet (or a failed one due for retry).
 * Idempotent — a device-day with a report is skipped, and the unique {@code (home_device_id, local_date)} index
 * rejects duplicates. Assumes a single instance; add a distributed lock (e.g. ShedLock) before running several.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "smarthome.reports", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DailyReportScheduler {

	private final DailyReportService dailyReportService;

	@Scheduled(fixedDelayString = "${smarthome.reports.tick:PT15M}",
			initialDelayString = "${smarthome.reports.initial-delay:PT1M}")
	public void generateDueDailyReports() {
		try {
			dailyReportService.generateDueReports();
		}
		catch (RuntimeException e) {
			log.error("Daily report tick failed", e);
		}
	}
}
