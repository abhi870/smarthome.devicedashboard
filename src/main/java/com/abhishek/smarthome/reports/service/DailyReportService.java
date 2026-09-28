package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.common.config.TaskExecutorsConfig;
import com.abhishek.smarthome.reports.dto.input.DailyReportTask;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The daily report job: plans the due device-days, then generates each in its own transaction, in parallel on the
 * {@link TaskExecutorsConfig#REPORT_EXECUTOR} pool. A failure is recorded on the report (FAILED,
 * {@code nextAttemptAt}) and does not stop the other devices. Deliberately not transactional itself. Safe to run
 * in parallel: every task writes different rows (its own report and rollups), the planner never returns the same
 * device-day twice, and a tick waits for all its tasks before the next one plans.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

	private final DailyReportPlanner dailyReportPlanner;
	private final ReportGenerator reportGenerator;
	@Qualifier(TaskExecutorsConfig.REPORT_EXECUTOR)
	private final Executor reportExecutor;

	/** @return the number of DAILY reports generated successfully */
	public int generateDueReports() {
		Instant now = Instant.now();
		List<DailyReportTask> tasks = dailyReportPlanner.findDueTasks(now);
		List<CompletableFuture<Boolean>> runs = tasks.stream()
				.map(task -> CompletableFuture.supplyAsync(() -> generate(task, now), reportExecutor)).toList();
		int generated = (int) runs.stream().filter(CompletableFuture::join).count();
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
