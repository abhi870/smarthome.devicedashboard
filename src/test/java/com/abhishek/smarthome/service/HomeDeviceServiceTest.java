package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.abhishek.smarthome.dto.input.homedevice.RegisterHomeDeviceCommand;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.DeviceNotFoundException;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.repository.HomeDeviceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HomeDeviceServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	private final HomeDeviceRepository repository = mock(HomeDeviceRepository.class);
	private final HomeService homeService = mock(HomeService.class);
	private final DeviceCatalogService deviceCatalogService = mock(DeviceCatalogService.class);
	private final HomeDeviceService service = new HomeDeviceService(repository, homeService, deviceCatalogService,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final Home home = Home.register("My home", "UTC", NOW);
	private final Device device = Device.register(Vendor.register(VendorCode.CISCO, "Cisco", NOW), DeviceType.TV,
			"CS-TV-9", "Cisco TV", List.of(), NOW);

	@Test
	void shouldRegisterEnabledHomeDeviceDueImmediately() {
		// given
		given(homeService.get(home.getId())).willReturn(home);
		given(deviceCatalogService.get(device.getId())).willReturn(device);
		given(repository.save(any(HomeDevice.class))).willAnswer(invocation -> invocation.getArgument(0));

		// when
		HomeDevice registered = service.register(
				new RegisterHomeDeviceCommand(home.getId(), device.getId(), "cs-tv-01", "Living room TV", 120));

		// then
		assertThat(registered.getHome()).isSameAs(home);
		assertThat(registered.getDevice()).isSameAs(device);
		assertThat(registered.getExternalDeviceId()).isEqualTo("cs-tv-01");
		assertThat(registered.getPollingIntervalSeconds()).isEqualTo(120);
		assertThat(registered.isEnabled()).isTrue();
		assertThat(registered.getNextPollAt()).isEqualTo(NOW);
		assertThat(registered.getLastPolledAt()).isNull();
		assertThat(registered.getCreatedAt()).isEqualTo(NOW);
		assertThat(registered.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void shouldNotSave_whenCatalogueDeviceUnknown() {
		// given
		given(homeService.get(home.getId())).willReturn(home);
		given(deviceCatalogService.get(device.getId())).willThrow(new DeviceNotFoundException(device.getId()));

		// when / then
		assertThatThrownBy(() -> service.register(
				new RegisterHomeDeviceCommand(home.getId(), device.getId(), "cs-tv-01", "Living room TV", 120)))
				.isInstanceOf(DeviceNotFoundException.class);
		then(repository).shouldHaveNoInteractions();
	}

	@Test
	void shouldThrowNotFound_whenHomeDeviceUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(repository.findWithDeviceById(id)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.get(id)).isInstanceOf(HomeDeviceNotFoundException.class);
	}
}
