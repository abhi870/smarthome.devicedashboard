package com.abhishek.smarthome.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class DeviceReadingRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

	@Autowired
	DeviceReadingRepository readings;

	@Autowired
	VendorRepository vendors;

	@Autowired
	DeviceRepository devices;

	@Autowired
	HomeRepository homes;

	@Autowired
	HomeDeviceRepository homeDevices;

	private UUID homeDeviceId;

	@BeforeEach
	void setUp() {
		Vendor vendor = vendors.saveAndFlush(Vendor.register(VendorCode.CISCO, "Cisco", T0));
		Device device = devices.saveAndFlush(Device.register(vendor, DeviceType.OVEN, "CS-OV20", "Cisco Oven",
				List.of(MetricMapping.of("pwr_w", MetricType.POWER, Conversion.NONE)), T0));
		Home home = homes.saveAndFlush(Home.register("My home", "UTC", T0));
		homeDeviceId = homeDevices.saveAndFlush(HomeDevice.register(home, device, "csc-oven-01", "Oven", 60, T0))
				.getId();
	}

	private DeviceReading reading(MetricType metric, double value, int minute) {
		return DeviceReading.record(homeDeviceId, metric, T0.plusSeconds(60L * minute), String.valueOf(value),
				metric.unit(), T0);
	}

	@Test
	void shouldReturnReadingsInHalfOpenRange_newestFirst() {
		// given: minutes 0..3, POWER and TEMPERATURE
		for (int minute = 0; minute < 4; minute++) {
			readings.save(reading(MetricType.POWER, 100 + minute, minute));
			readings.save(reading(MetricType.TEMPERATURE, 20 + minute, minute));
		}
		readings.flush();

		// when: [minute 1, minute 3)
		List<DeviceReading> all = readings.findInRange(homeDeviceId, T0.plusSeconds(60), T0.plusSeconds(180));
		List<DeviceReading> power = readings.findInRange(homeDeviceId, MetricType.POWER, T0.plusSeconds(60),
				T0.plusSeconds(180));

		// then: start inclusive, end exclusive, newest first
		assertThat(all).hasSize(4).extracting(DeviceReading::getTime)
				.containsExactly(T0.plusSeconds(120), T0.plusSeconds(120), T0.plusSeconds(60), T0.plusSeconds(60));
		assertThat(power).extracting(DeviceReading::getValue).containsExactly("102.0", "101.0");
		assertThat(power).extracting(DeviceReading::getUnit).containsOnly("W");
	}

	@Test
	void shouldStoreSwitchStateAsText() {
		readings.saveAndFlush(DeviceReading.record(homeDeviceId, MetricType.SWITCH, T0, "ON", "on/off", T0));

		assertThat(readings.findInRange(homeDeviceId, MetricType.SWITCH, T0, T0.plusSeconds(60)))
				.singleElement().extracting(DeviceReading::getValue).isEqualTo("ON");
	}

	@Test
	void shouldRejectDuplicateReading_forSameDeviceMetricAndTime() {
		readings.saveAndFlush(reading(MetricType.POWER, 100, 0));

		assertThatThrownBy(() -> readings.saveAndFlush(reading(MetricType.POWER, 999, 0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
