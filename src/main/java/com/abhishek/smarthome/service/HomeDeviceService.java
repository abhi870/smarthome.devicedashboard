package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.homedevice.CollectionTarget;
import com.abhishek.smarthome.dto.input.homedevice.RegisterHomeDeviceCommand;
import com.abhishek.smarthome.dto.output.homedevice.HomeDeviceResponse;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.repository.HomeDeviceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Users register physical appliances (of a supported catalogue device) in their homes. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeDeviceService {

	private final HomeDeviceRepository homeDeviceRepository;
	private final HomeService homeService;
	private final DeviceCatalogService deviceCatalogService;
	private final VendorService vendorService;

	/**
	 * Registers an appliance in a home; its first metrics collection is due immediately.
	 *
	 * @throws com.abhishek.smarthome.exception.HomeNotFoundException if the home does not exist
	 * @throws com.abhishek.smarthome.exception.DeviceNotFoundException if the catalogue device does not exist
	 */
	@Transactional
	public HomeDeviceResponse register(RegisterHomeDeviceCommand command) {
		Home home = homeService.getHome(command.getHomeId());
		Device device = deviceCatalogService.getDevice(command.getDeviceId());
		Vendor vendor = vendorService.getVendor(device.getVendorId());
		HomeDevice homeDevice = homeDeviceRepository.save(HomeDevice.register(home, device, command.getExternalDeviceId(),
				command.getName(), command.getPollingIntervalSeconds(), Instant.now()));
		log.info("Registered home device {} ({} {} {}) in home {}", homeDevice.getId(), vendor.getCode(),
				device.getModel(), homeDevice.getExternalDeviceId(), home.getId());
		return HomeDeviceResponse.from(homeDevice, device, vendor);
	}

	/** Home devices, optionally of one home, ordered by name. */
	public List<HomeDeviceResponse> list(@Nullable UUID homeId) {
		Sort byName = Sort.by("name");
		List<HomeDevice> homeDevices = homeId == null ? homeDeviceRepository.findAllBy(byName)
				: homeDeviceRepository.findByHomeId(homeId, byName);
		return withDeviceAndVendor(homeDevices, HomeDeviceResponse::from);
	}

	/**
	 * Changes how often metrics are collected and reschedules the next run.
	 *
	 * @throws HomeDeviceNotFoundException if no home device has this id
	 * @throws com.abhishek.smarthome.exception.InvalidPollingIntervalException if out of range
	 */
	@Transactional
	public HomeDeviceResponse changePollingInterval(UUID id, int seconds) {
		HomeDevice homeDevice = getHomeDevice(id);
		homeDevice.changePollingInterval(seconds, Instant.now());
		return toResponse(homeDevice);
	}

	/**
	 * @throws HomeDeviceNotFoundException if no home device has this id
	 */
	public HomeDeviceResponse get(UUID id) {
		return toResponse(getHomeDevice(id));
	}

	/** All enabled home devices (for the daily report job). */
	public List<HomeDevice> findAllEnabled() {
		return homeDeviceRepository.findByEnabledTrue();
	}

	/** Enabled home devices due for collection now, most overdue first (for the collection scheduler). */
	public List<HomeDevice> findDue(int limit) {
		return homeDeviceRepository.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant.now(),
				Limit.of(limit));
	}

	/**
	 * Like {@link #findDue}, as detached snapshots the collector can use outside a transaction. Fixed number of
	 * queries whatever the batch size: due home devices, their catalogue devices, their vendors, and the devices'
	 * metric mappings (batch-fetched).
	 */
	public List<CollectionTarget> findDueTargets(int limit) {
		return withDeviceAndVendor(findDue(limit), CollectionTarget::of);
	}

	/**
	 * Records a successful collection covering up to {@code runAt}; the next run is one interval later.
	 *
	 * @throws HomeDeviceNotFoundException if no home device has this id
	 */
	@Transactional
	public void recordRun(UUID id, Instant runAt) {
		getHomeDevice(id).markRun(runAt);
	}

	/**
	 * Records a failed collection attempt; retried after {@code delay} (at most one interval).
	 *
	 * @throws HomeDeviceNotFoundException if no home device has this id
	 */
	@Transactional
	public void scheduleRetry(UUID id, Duration delay) {
		getHomeDevice(id).scheduleRetry(Instant.now(), delay);
	}

	/**
	 * The entity itself, for other services.
	 *
	 * @throws HomeDeviceNotFoundException if no home device has this id
	 */
	public HomeDevice getHomeDevice(UUID id) {
		return homeDeviceRepository.findById(id).orElseThrow(() -> new HomeDeviceNotFoundException(id));
	}

	private HomeDeviceResponse toResponse(HomeDevice homeDevice) {
		Device device = deviceCatalogService.getDevice(homeDevice.getDeviceId());
		return HomeDeviceResponse.from(homeDevice, device, vendorService.getVendor(device.getVendorId()));
	}

	/**
	 * Loads the catalogue devices of all {@code homeDevices} in one query and their vendors in another, then maps each
	 * home device together with its device and vendor.
	 */
	private <T> List<T> withDeviceAndVendor(List<HomeDevice> homeDevices, HomeDeviceMapper<T> mapper) {
		Map<UUID, Device> devicesById = deviceCatalogService
				.getDevicesById(EntityLookups.idsOf(homeDevices, HomeDevice::getDeviceId));
		Map<UUID, Vendor> vendorsById = vendorService
				.getVendorsById(EntityLookups.idsOf(devicesById.values(), Device::getVendorId));
		return homeDevices.stream().map(homeDevice -> {
			Device device = devicesById.get(homeDevice.getDeviceId());
			return mapper.map(homeDevice, device, vendorsById.get(device.getVendorId()));
		}).toList();
	}

	/** Builds a result from a home device, its catalogue device and that device's vendor. */
	@FunctionalInterface
	private interface HomeDeviceMapper<T> {
		T map(HomeDevice homeDevice, Device device, Vendor vendor);
	}
}
