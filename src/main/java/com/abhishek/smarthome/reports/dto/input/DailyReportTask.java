package com.abhishek.smarthome.reports.dto.input;

import com.abhishek.smarthome.enums.MetricType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * One device-day the daily job has to report: copied out of the entities inside a read-only transaction, so each
 * task can then be generated (or fail) in its own transaction.
 */
@Getter
public final class DailyReportTask {

	private final UUID homeDeviceId;
	private final ZoneId zone;
	private final LocalDate localDate;
	/** The metrics the device's catalogue model maps. */
	private final Set<MetricType> metrics;

	public DailyReportTask(UUID homeDeviceId, ZoneId zone, LocalDate localDate, Set<MetricType> metrics) {
		this.homeDeviceId = homeDeviceId;
		this.zone = zone;
		this.localDate = localDate;
		this.metrics = Set.copyOf(metrics);
	}

	@Override
	public String toString() {
		return "DailyReportTask[homeDevice=" + homeDeviceId + ", date=" + localDate + ", zone=" + zone + "]";
	}
}
