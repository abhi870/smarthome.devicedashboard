package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.device.RegisterDeviceCommand;
import com.abhishek.smarthome.dto.output.device.DeviceResponse;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.exception.DeviceNotFoundException;
import com.abhishek.smarthome.exception.InvalidMetricMappingException;
import com.abhishek.smarthome.repository.DeviceRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue of supported device models: admins add entries, users browse them. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceCatalogService {

	private static final Sort BY_NAME = Sort.by("name");

	private final DeviceRepository deviceRepository;
	private final VendorService vendorService;

	/**
	 * Adds a supported device model for a vendor, with its metric mappings.
	 *
	 * @throws com.abhishek.smarthome.exception.VendorNotFoundException if the vendor does not exist
	 */
	@Transactional
	public DeviceResponse register(RegisterDeviceCommand command) {
		Vendor vendor = vendorService.getVendor(command.getVendorId());
		Device device = deviceRepository.save(Device.register(vendor, command.getDeviceType(), command.getModel(),
				command.getName(), command.getMetricMappings(), Instant.now()));
		return DeviceResponse.from(device, vendor);
	}

	/**
	 * Replaces the metric mappings of a catalogue device; home devices of this model use them from the next collection.
	 *
	 * @throws DeviceNotFoundException if no catalogue device has this id
	 * @throws InvalidMetricMappingException if two mappings use the same external metric
	 */
	@Transactional
	public DeviceResponse replaceMetricMappings(UUID id, List<MetricMapping> mappings) {
		Device device = getDevice(id);
		device.replaceMetricMappings(mappings);
		return toResponse(device);
	}

	/**
	 * @throws DeviceNotFoundException if no catalogue device has this id
	 */
	public DeviceResponse get(UUID id) {
		return toResponse(getDevice(id));
	}

	/** Supported devices, optionally filtered by vendor and/or type, ordered by name. */
	public List<DeviceResponse> list(@Nullable UUID vendorId, @Nullable DeviceType deviceType) {
		return toResponses(findDevices(vendorId, deviceType));
	}

	/**
	 * The entity itself, for other services (e.g. to register a home device of this model).
	 *
	 * @throws DeviceNotFoundException if no catalogue device has this id
	 */
	public Device getDevice(UUID id) {
		return deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
	}

	/** Catalogue devices with these ids, keyed by id (one query), for other services assembling related data. */
	public Map<UUID, Device> getDevicesById(Collection<UUID> ids) {
		return EntityLookups.indexById(deviceRepository.findAllById(ids), Device::getId);
	}

	private DeviceResponse toResponse(Device device) {
		return DeviceResponse.from(device, vendorService.getVendor(device.getVendorId()));
	}

	/** Loads the vendors of all devices in one query and pairs each device with its vendor. */
	private List<DeviceResponse> toResponses(List<Device> devices) {
		Map<UUID, Vendor> vendorsById = vendorService.getVendorsById(EntityLookups.idsOf(devices, Device::getVendorId));
		return devices.stream().map(device -> DeviceResponse.from(device, vendorsById.get(device.getVendorId()))).toList();
	}

	private List<Device> findDevices(@Nullable UUID vendorId, @Nullable DeviceType deviceType) {
		if (vendorId != null && deviceType != null) {
			return deviceRepository.findByVendorIdAndDeviceType(vendorId, deviceType, BY_NAME);
		}
		if (vendorId != null) {
			return deviceRepository.findByVendorId(vendorId, BY_NAME);
		}
		if (deviceType != null) {
			return deviceRepository.findByDeviceType(deviceType, BY_NAME);
		}
		return deviceRepository.findAllBy(BY_NAME);
	}
}
