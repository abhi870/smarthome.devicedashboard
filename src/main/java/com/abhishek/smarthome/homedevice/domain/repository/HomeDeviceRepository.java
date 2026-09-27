package com.abhishek.smarthome.homedevice.domain.repository;

import com.abhishek.smarthome.homedevice.domain.entity.HomeDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeDeviceRepository extends JpaRepository<HomeDevice, UUID> {

	/** Loads the catalogue device and its vendor in the same query. */
	@EntityGraph(attributePaths = { "device", "device.vendor" })
	Optional<HomeDevice> findWithDeviceById(UUID id);

	@EntityGraph(attributePaths = { "device", "device.vendor" })
	List<HomeDevice> findAllBy(Sort sort);

	@EntityGraph(attributePaths = { "device", "device.vendor" })
	List<HomeDevice> findByHomeId(UUID homeId, Sort sort);
}
