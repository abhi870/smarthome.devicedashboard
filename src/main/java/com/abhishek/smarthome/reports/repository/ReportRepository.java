package com.abhishek.smarthome.reports.repository;

import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	/** The DAILY report of a device and day (unique {@code (home_device_id, local_date)}). */
	Optional<Report> findByTypeAndHomeDeviceIdAndLocalDate(ReportType type, UUID homeDeviceId, LocalDate localDate);

	/** DAILY reports of these devices from {@code firstDate} on: the daily job skips device-days already done. */
	List<Report> findByTypeAndHomeDeviceIdInAndLocalDateGreaterThanEqual(ReportType type, Collection<UUID> homeDeviceIds,
			LocalDate firstDate);

	/** Reports of a device whose period starts in {@code [from, to)}, newest first. */
	List<Report> findByHomeDeviceIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartDesc(
			UUID homeDeviceId, Instant from, Instant to);

	/** Like above, of one type. */
	List<Report> findByHomeDeviceIdAndTypeAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartDesc(
			UUID homeDeviceId, ReportType type, Instant from, Instant to);
}
