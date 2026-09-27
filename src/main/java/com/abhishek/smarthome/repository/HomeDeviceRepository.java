package com.abhishek.smarthome.repository;

import com.abhishek.smarthome.entity.HomeDevice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeDeviceRepository extends JpaRepository<HomeDevice, UUID> {

	/** Loads the catalogue device and its vendor in the same query. */
	@EntityGraph(attributePaths = { "device", "device.vendor" })
	Optional<HomeDevice> findWithDeviceById(UUID id);

	@EntityGraph(attributePaths = { "device", "device.vendor" })
	List<HomeDevice> findAllBy(Sort sort);

	/**
	 * Enabled home devices whose next run is due ({@code nextRunAt <= now}), most overdue first, at most
	 * {@code limit}. Served by index {@code (enabled, next_run_at)}.
	 */
	@EntityGraph(attributePaths = { "device", "device.vendor" })
	List<HomeDevice> findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant now, Limit limit);

	@EntityGraph(attributePaths = { "device", "device.vendor" })
	List<HomeDevice> findByHomeId(UUID homeId, Sort sort);
}
