package com.abhishek.smarthome.repository;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Readings of one home device over a half-open time range {@code [start, end)}, newest first. The per-metric query
 * is served by the unique index {@code (home_device_id, metric, reading_time DESC)}.
 */
public interface DeviceReadingRepository extends JpaRepository<DeviceReading, UUID> {

	@Query("""
			select r from DeviceReading r
			where r.homeDeviceId = :homeDeviceId and r.metric = :metric
			  and r.time >= :start and r.time < :end
			order by r.time desc""")
	List<DeviceReading> findInRange(@Param("homeDeviceId") UUID homeDeviceId, @Param("metric") MetricType metric,
			@Param("start") Instant start, @Param("end") Instant end);

	@Query("""
			select r from DeviceReading r
			where r.homeDeviceId = :homeDeviceId and r.time >= :start and r.time < :end
			order by r.time desc, r.metric""")
	List<DeviceReading> findInRange(@Param("homeDeviceId") UUID homeDeviceId, @Param("start") Instant start,
			@Param("end") Instant end);
}
