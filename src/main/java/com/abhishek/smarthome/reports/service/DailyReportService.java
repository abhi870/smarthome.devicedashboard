package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.reports.dto.input.DailyReportTask;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The daily report job: plans the due device-days, then generates each in its own transaction. A failure is
 * recorded on the report (FAILED, {@code nextAttemptAt}) and does not stop the other devices. Deliberately not
 * transactional itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

	private final DailyReportPlanner dailyReportPlanner;
	private final ReportGenerator reportGenerator;

	/** @return the number of DAILY reports generated successfully */
	public int generateDueReports() {
		Instant now = Instant.now();
		List<DailyReportTask> tasks = dailyReportPlanner.findDueTasks(now);
		int generated = 0;
		for (DailyReportTask task : tasks) {
			generated += generate(task, now) ? 1 : 0;
		}
		if (!tasks.isEmpty()) {
			log.info("Daily reports: {} of {} due device-days generated", generated, tasks.size());
		}
		return generated;
	}

	private boolean generate(DailyReportTask task, Instant now) {
		try {
			reportGenerator.generateDaily(task, now);
			return true;
		}
		catch (RuntimeException e) {
			log.warn("Daily report failed for {}: {}", task, e.toString());
			recordFailure(task, e, now);
			return false;
		}
	}

	private void recordFailure(DailyReportTask task, RuntimeException cause, Instant now) {
		try {
			reportGenerator.recordDailyFailure(task, String.valueOf(cause.getMessage()), now);
		}
		catch (RuntimeException e) {
			log.error("Could not record daily report failure for {}", task, e);
		}
	}
}
