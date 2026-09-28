package com.abhishek.smarthome.repository;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.enums.DeviceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Catalogue lookups. Vendors are loaded separately by id ({@link VendorRepository#findAllById}); metric mappings (a
 * value collection of the device) are batch-fetched with one {@code IN (...)} query per list.
 */
public interface DeviceRepository extends JpaRepository<Device, UUID> {

	List<Device> findAllBy(Sort sort);

	List<Device> findByVendorId(UUID vendorId, Sort sort);

	List<Device> findByDeviceType(DeviceType deviceType, Sort sort);

	List<Device> findByVendorIdAndDeviceType(UUID vendorId, DeviceType deviceType, Sort sort);
}
