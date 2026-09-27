package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.homedevice.RegisterHomeDeviceCommand;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.repository.HomeDeviceRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Users register physical appliances (of a supported catalogue device) in their homes. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeDeviceService {

	private final HomeDeviceRepository repository;
	private final HomeService homeService;
	private final DeviceCatalogService deviceCatalogService;
	private final Clock clock;

	/**
	 * Registers an appliance in a home; its first metrics collection is due immediately.
	 *
	 * @throws com.abhishek.smarthome.exception.HomeNotFoundException if the home does not exist
	 * @throws com.abhishek.smarthome.exception.DeviceNotFoundException if the catalogue device does not exist
	 */
	@Transactional
	public HomeDevice register(RegisterHomeDeviceCommand command) {
		Home home = homeService.get(command.getHomeId());
		Device device = deviceCatalogService.get(command.getDeviceId());
		HomeDevice homeDevice = repository.save(HomeDevice.register(home, device, command.getExternalDeviceId(),
				command.getName(), command.getPollingIntervalSeconds(), clock.instant()));
		log.info("Registered home device {} ({} {} {}) in home {}", homeDevice.getId(), device.getVendor().getCode(),
				device.getModel(), homeDevice.getExternalDeviceId(), home.getId());
		return homeDevice;
	}

	/** Home devices, optionally of one home, ordered by name. */
	public List<HomeDevice> list(@Nullable UUID homeId) {
		Sort byName = Sort.by("name");
		return homeId == null ? repository.findAllBy(byName) : repository.findByHomeId(homeId, byName);
	}

	/**
	 * @throws HomeDeviceNotFoundException if no home device has this id
	 */
	public HomeDevice get(UUID id) {
		return repository.findWithDeviceById(id).orElseThrow(() -> new HomeDeviceNotFoundException(id));
	}
}
