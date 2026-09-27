package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.device.RegisterDeviceCommand;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.exception.DeviceNotFoundException;
import com.abhishek.smarthome.exception.InvalidMetricMappingException;
import com.abhishek.smarthome.repository.DeviceRepository;
import java.time.Clock;
import java.util.List;
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

	private final DeviceRepository repository;
	private final VendorService vendorService;
	private final Clock clock;

	/**
	 * Adds a supported device model for a vendor, with its metric mappings.
	 *
	 * @throws com.abhishek.smarthome.exception.VendorNotFoundException if the vendor does not exist
	 */
	@Transactional
	public Device register(RegisterDeviceCommand command) {
		Vendor vendor = vendorService.get(command.getVendorId());
		return repository.save(Device.register(vendor, command.getDeviceType(), command.getModel(), command.getName(),
				command.getMetricMappings(), clock.instant()));
	}

	/**
	 * Replaces the metric mappings of a catalogue device; home devices of this model use them from the next collection.
	 *
	 * @throws DeviceNotFoundException if no catalogue device has this id
	 * @throws InvalidMetricMappingException if two mappings use the same external metric
	 */
	@Transactional
	public Device replaceMetricMappings(UUID id, List<MetricMapping> mappings) {
		Device device = get(id);
		device.replaceMetricMappings(mappings);
		return device;
	}

	/**
	 * @throws DeviceNotFoundException if no catalogue device has this id
	 */
	public Device get(UUID id) {
		return repository.findWithVendorById(id).orElseThrow(() -> new DeviceNotFoundException(id));
	}

	/** Supported devices, optionally filtered by vendor and/or type, ordered by name. */
	public List<Device> list(@Nullable UUID vendorId, @Nullable DeviceType deviceType) {
		if (vendorId != null && deviceType != null) {
			return repository.findByVendorIdAndDeviceType(vendorId, deviceType, BY_NAME);
		}
		if (vendorId != null) {
			return repository.findByVendorId(vendorId, BY_NAME);
		}
		if (deviceType != null) {
			return repository.findByDeviceType(deviceType, BY_NAME);
		}
		return repository.findAllBy(BY_NAME);
	}
}
