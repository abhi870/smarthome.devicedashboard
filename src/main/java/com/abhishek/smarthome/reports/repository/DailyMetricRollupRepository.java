package com.abhishek.smarthome.reports.repository;

import com.abhishek.smarthome.reports.entity.DailyMetricRollup;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Served by the unique index {@code (home_device_id, local_date, metric)}. */
public interface DailyMetricRollupRepository extends JpaRepository<DailyMetricRollup, UUID> {

	List<DailyMetricRollup> findByHomeDeviceIdAndLocalDateBetween(UUID homeDeviceId, LocalDate firstDate,
			LocalDate lastDate);

	/** Removes a day's rollups before they are recomputed (bulk delete, runs before the new rows are inserted). */
	@Modifying
	@Query("delete from DailyMetricRollup r where r.homeDeviceId = :homeDeviceId and r.localDate = :localDate")
	int deleteDay(@Param("homeDeviceId") UUID homeDeviceId, @Param("localDate") LocalDate localDate);
}
