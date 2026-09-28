package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.abhishek.smarthome.dto.input.homedevice.CollectionTarget;
import com.abhishek.smarthome.dto.input.homedevice.RegisterHomeDeviceCommand;
import com.abhishek.smarthome.dto.output.homedevice.HomeDeviceResponse;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.DeviceNotFoundException;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.repository.HomeDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

class HomeDeviceServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	private final HomeDeviceRepository homeDeviceRepository = mock(HomeDeviceRepository.class);
	private final HomeService homeService = mock(HomeService.class);
	private final DeviceCatalogService deviceCatalogService = mock(DeviceCatalogService.class);
	private final VendorService vendorService = mock(VendorService.class);
	private final HomeDeviceService homeDeviceService = new HomeDeviceService(homeDeviceRepository, homeService,
			deviceCatalogService, vendorService);

	private final Home home = Home.register("My home", "UTC", NOW);
	private final Vendor cisco = Vendor.register(VendorCode.CISCO, "Cisco", NOW);
	private final Device device = Device.register(cisco, DeviceType.TV, "CS-TV-9", "Cisco TV", List.of(), NOW);

	@Test
	void shouldRegisterEnabledHomeDeviceDueImmediately() {
		// given
		given(homeService.getHome(home.getId())).willReturn(home);
		given(deviceCatalogService.getDevice(device.getId())).willReturn(device);
		given(vendorService.getVendor(cisco.getId())).willReturn(cisco);
		given(homeDeviceRepository.save(any(HomeDevice.class))).willAnswer(invocation -> invocation.getArgument(0));

		// when
		Instant before = Instant.now();
		HomeDeviceResponse registered = homeDeviceService.register(
				new RegisterHomeDeviceCommand(home.getId(), device.getId(), "cs-tv-01", "Living room TV", 120));
		Instant after = Instant.now();

		// then
		assertThat(registered.getHomeId()).isEqualTo(home.getId());
		assertThat(registered.getDeviceId()).isEqualTo(device.getId());
		assertThat(registered.getVendorCode()).isEqualTo(VendorCode.CISCO);
		assertThat(registered.getExternalDeviceId()).isEqualTo("cs-tv-01");
		assertThat(registered.getPollingIntervalSeconds()).isEqualTo(120);
		assertThat(registered.isEnabled()).isTrue();
		assertThat(registered.getCreatedAt()).isBetween(before, after);
		assertThat(registered.getNextRunAt()).isEqualTo(registered.getCreatedAt()); // due immediately
		assertThat(registered.getUpdatedAt()).isEqualTo(registered.getCreatedAt());
		assertThat(registered.getLastRunAt()).isNull();
	}

	@Test
	void shouldNotSave_whenCatalogueDeviceUnknown() {
		// given
		given(homeService.getHome(home.getId())).willReturn(home);
		given(deviceCatalogService.getDevice(device.getId())).willThrow(new DeviceNotFoundException(device.getId()));

		// when / then
		assertThatThrownBy(() -> homeDeviceService.register(
				new RegisterHomeDeviceCommand(home.getId(), device.getId(), "cs-tv-01", "Living room TV", 120)))
				.isInstanceOf(DeviceNotFoundException.class);
		then(homeDeviceRepository).shouldHaveNoInteractions();
	}

	@Test
	void shouldThrowNotFound_whenHomeDeviceUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(homeDeviceRepository.findById(id)).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> homeDeviceService.get(id)).isInstanceOf(HomeDeviceNotFoundException.class);
	}

	@Test
	void shouldChangePollingInterval_andRescheduleNextRun() {
		// given: last run 10 minutes ago with a 1-hour interval
		Instant now = Instant.now();
		HomeDevice homeDevice = HomeDevice.register(home, device, "cs-tv-01", "TV", 3600, now.minusSeconds(3600));
		homeDevice.markRun(now.minusSeconds(600));
		given(homeDeviceRepository.findById(homeDevice.getId())).willReturn(Optional.of(homeDevice));
		given(deviceCatalogService.getDevice(device.getId())).willReturn(device);
		given(vendorService.getVendor(cisco.getId())).willReturn(cisco);

		// when: 5 minutes -> next run is already past, so it is due now
		HomeDeviceResponse changed = homeDeviceService.changePollingInterval(homeDevice.getId(), 300);

		// then
		assertThat(changed.getPollingIntervalSeconds()).isEqualTo(300);
		assertThat(changed.getNextRunAt()).isBetween(now, Instant.now());
	}

	@Test
	void shouldFindDueDevicesAtCurrentTime() {
		// when
		Instant before = Instant.now();
		homeDeviceService.findDue(50);
		Instant after = Instant.now();

		// then
		ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
		then(homeDeviceRepository).should()
				.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now.capture(), eq(Limit.of(50)));
		assertThat(now.getValue()).isBetween(before, after);
	}

	@Test
	void shouldListHomeDevices_withDevicesAndVendorsLoadedInBulk() {
		// given: two home devices of two catalogue devices from two vendors
		Vendor samsung = Vendor.register(VendorCode.SAMSUNG, "Samsung", NOW);
		Device fridge = Device.register(samsung, DeviceType.REFRIGERATOR, "SS-RF-1", "Samsung Fridge", List.of(), NOW);
		HomeDevice tv = HomeDevice.register(home, device, "cs-tv-01", "TV", 60, NOW);
		HomeDevice kitchenFridge = HomeDevice.register(home, fridge, "ss-rf-01", "Fridge", 60, NOW);
		given(homeDeviceRepository.findAllBy(any())).willReturn(List.of(kitchenFridge, tv));
		given(deviceCatalogService.getDevicesById(Set.of(device.getId(), fridge.getId())))
				.willReturn(Map.of(device.getId(), device, fridge.getId(), fridge));
		given(vendorService.getVendorsById(Set.of(cisco.getId(), samsung.getId())))
				.willReturn(Map.of(cisco.getId(), cisco, samsung.getId(), samsung));

		// when
		List<HomeDeviceResponse> listed = homeDeviceService.list(null);

		// then: order kept, each paired with its own device and vendor
		assertThat(listed).extracting(HomeDeviceResponse::getExternalDeviceId, HomeDeviceResponse::getModel,
				HomeDeviceResponse::getVendorCode)
				.containsExactly(tuple("ss-rf-01", "SS-RF-1", VendorCode.SAMSUNG), tuple("cs-tv-01", "CS-TV-9", VendorCode.CISCO));
	}

	@Test
	void shouldBuildCollectionTargets_withVendorOfEachDevice() {
		// given
		HomeDevice tv = HomeDevice.register(home, device, "cs-tv-01", "TV", 60, NOW);
		given(homeDeviceRepository.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(), any()))
				.willReturn(List.of(tv));
		given(deviceCatalogService.getDevicesById(Set.of(device.getId()))).willReturn(Map.of(device.getId(), device));
		given(vendorService.getVendorsById(Set.of(cisco.getId()))).willReturn(Map.of(cisco.getId(), cisco));

		// when
		List<CollectionTarget> targets = homeDeviceService.findDueTargets(50);

		// then
		assertThat(targets).singleElement().satisfies(target -> {
			assertThat(target.getHomeDeviceId()).isEqualTo(tv.getId());
			assertThat(target.getVendorCode()).isEqualTo(VendorCode.CISCO);
			assertThat(target.getExternalDeviceId()).isEqualTo("cs-tv-01");
		});
	}
}
