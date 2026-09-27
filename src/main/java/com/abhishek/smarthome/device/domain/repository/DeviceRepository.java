package com.abhishek.smarthome.device.domain.repository;

import com.abhishek.smarthome.device.domain.entity.Device;
import com.abhishek.smarthome.device.domain.entity.DeviceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Catalogue lookups fetch the vendor and metric mappings in the same query (no N+1 when listing). */
public interface DeviceRepository extends JpaRepository<Device, UUID> {

	@EntityGraph(attributePaths = { "vendor", "metricMappings" })
	Optional<Device> findWithVendorById(UUID id);

	@EntityGraph(attributePaths = { "vendor", "metricMappings" })
	List<Device> findAllBy(Sort sort);

	@EntityGraph(attributePaths = { "vendor", "metricMappings" })
	List<Device> findByVendorId(UUID vendorId, Sort sort);

	@EntityGraph(attributePaths = { "vendor", "metricMappings" })
	List<Device> findByDeviceType(DeviceType deviceType, Sort sort);

	@EntityGraph(attributePaths = { "vendor", "metricMappings" })
	List<Device> findByVendorIdAndDeviceType(UUID vendorId, DeviceType deviceType, Sort sort);
}
