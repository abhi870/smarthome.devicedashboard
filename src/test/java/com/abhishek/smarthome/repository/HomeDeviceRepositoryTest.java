package com.abhishek.smarthome.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.enums.VendorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

/** Relationships and constraints of vendor → device (catalogue) → home device ← home. */
@DataJpaTest
class HomeDeviceRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	@Autowired
	VendorRepository vendors;

	@Autowired
	HomeRepository homes;

	@Autowired
	DeviceRepository devices;

	@Autowired
	HomeDeviceRepository homeDevices;

	@Autowired
	EntityManager em;

	private Vendor vendor;
	private Home home;
	private Device device;

	@BeforeEach
	void setUp() {
		vendor = vendors.saveAndFlush(Vendor.register(VendorCode.SAMSUNG, "Samsung", NOW));
		home = homes.saveAndFlush(Home.register("My home", "UTC", NOW));
		device = devices.saveAndFlush(Device.register(vendor, DeviceType.REFRIGERATOR, "SS-RF-1", "Samsung Fridge",
				List.of(MetricMapping.of("temperatureMeasurement.temperature", MetricType.TEMPERATURE, Conversion.F_TO_C),
						MetricMapping.of("powerConsumptionReport.energy", MetricType.ENERGY, Conversion.WH_TO_KWH)),
				NOW));
	}

	@Test
	void shouldLoadDeviceAndVendor_whenFoundWithEntityGraph() {
		// given
		HomeDevice saved = homeDevices.saveAndFlush(HomeDevice.register(home, device, "ss-rf-01", "Kitchen fridge", 60, NOW));
		em.clear();

		// when
		HomeDevice found = homeDevices.findWithDeviceById(saved.getId()).orElseThrow();
		em.clear();

		// then: associations are initialized, so reading them outside the persistence context works
		assertThat(found.getDevice().getModel()).isEqualTo("SS-RF-1");
		assertThat(found.getDevice().getVendor().getCode()).isEqualTo(VendorCode.SAMSUNG);
	}

	@Test
	void shouldPersistMetricMappings_withDevice() {
		// given
		em.clear();

		// when
		Device found = devices.findWithVendorById(device.getId()).orElseThrow();
		em.clear();

		// then: mappings were saved with the device and are fetched by the entity graph
		assertThat(found.getMetricMappings())
				.extracting(MetricMapping::getExternalMetric, MetricMapping::getMetric, MetricMapping::getExternalUnit)
				.containsExactlyInAnyOrder(
						tuple("temperatureMeasurement.temperature", MetricType.TEMPERATURE,
								"F"),
						tuple("powerConsumptionReport.energy", MetricType.ENERGY,
								"Wh"));
	}

	@Test
	void shouldReplaceMetricMappings() {
		// given
		Device loaded = devices.findWithVendorById(device.getId()).orElseThrow();

		// when
		loaded.replaceMetricMappings(List.of(MetricMapping.of("switch.switch", MetricType.SWITCH, Conversion.NONE)));
		devices.flush();
		em.clear();

		// then
		assertThat(devices.findWithVendorById(device.getId()).orElseThrow().getMetricMappings())
				.extracting(MetricMapping::getExternalMetric).containsExactly("switch.switch");
	}

	@Test
	void shouldExposeInverseSides_forVendorAndHome() {
		// given
		homeDevices.saveAndFlush(HomeDevice.register(home, device, "ss-rf-01", "Kitchen fridge", 60, NOW));
		homeDevices.saveAndFlush(HomeDevice.register(home, device, "ss-rf-02", "Garage fridge", 60, NOW));
		em.clear();

		// when
		Vendor reloadedVendor = em.find(Vendor.class, vendor.getId());
		Home reloadedHome = em.find(Home.class, home.getId());

		// then
		assertThat(reloadedVendor.getDevices()).extracting(Device::getModel).containsExactly("SS-RF-1");
		assertThat(reloadedHome.getDevices()).extracting(HomeDevice::getExternalDeviceId)
				.containsExactlyInAnyOrder("ss-rf-01", "ss-rf-02");
	}

	@Test
	void shouldRejectDuplicateExternalId_forSameCatalogueDevice() {
		// given
		homeDevices.saveAndFlush(HomeDevice.register(home, device, "ss-rf-01", "Kitchen fridge", 60, NOW));

		// when / then
		assertThatThrownBy(() -> homeDevices
				.saveAndFlush(HomeDevice.register(home, device, "ss-rf-01", "Duplicate", 60, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldRejectDuplicateModel_forSameVendor() {
		assertThatThrownBy(() -> devices
				.saveAndFlush(Device.register(vendor, DeviceType.REFRIGERATOR, "SS-RF-1", "Duplicate", List.of(), NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldFindDueDevices_mostOverdueFirst_andSkipFutureOnes() {
		// given: registered at NOW (due at NOW); one ran at NOW (next NOW+60), one registered later
		HomeDevice dueFirst = homeDevices.saveAndFlush(
				HomeDevice.register(home, device, "ss-rf-01", "A", 60, NOW.minusSeconds(120)));
		HomeDevice dueSecond = homeDevices.saveAndFlush(HomeDevice.register(home, device, "ss-rf-02", "B", 60, NOW));
		HomeDevice notDue = HomeDevice.register(home, device, "ss-rf-03", "C", 60, NOW);
		notDue.markRun(NOW);
		homeDevices.saveAndFlush(notDue);
		em.clear();

		// when
		List<HomeDevice> due = homeDevices.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(NOW,
				Limit.of(10));

		// then
		assertThat(due).extracting(HomeDevice::getId).containsExactly(dueFirst.getId(), dueSecond.getId());
		assertThat(homeDevices.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(NOW, Limit.of(1)))
				.hasSize(1);
	}
}
