package com.abhishek.smarthome.repository;

import com.abhishek.smarthome.entity.HomeDevice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/** Home device lookups. Catalogue devices and vendors are loaded separately by id, never navigated. */
public interface HomeDeviceRepository extends JpaRepository<HomeDevice, UUID> {

	List<HomeDevice> findAllBy(Sort sort);

	/**
	 * Enabled home devices whose next run is due ({@code nextRunAt <= now}), most overdue first, at most
	 * {@code limit}. Served by index {@code (enabled, next_run_at)}.
	 */
	List<HomeDevice> findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant now, Limit limit);

	List<HomeDevice> findByHomeId(UUID homeId, Sort sort);

	List<HomeDevice> findByEnabledTrue();
}
