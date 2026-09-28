package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.dto.input.homedevice.CollectionTarget;
import com.abhishek.smarthome.dto.output.homedevice.HomeDeviceResponse;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.repository.DeviceRepository;
import com.abhishek.smarthome.repository.HomeDeviceRepository;
import com.abhishek.smarthome.repository.HomeRepository;
import com.abhishek.smarthome.repository.VendorRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Related rows are loaded in bulk through repositories, so the number of SQL statements is fixed whatever the number
 * of home devices (no N+1, and the scheduler's limit is applied in SQL).
 */
@DataJpaTest
@Import({ HomeDeviceService.class, HomeService.class, DeviceCatalogService.class, VendorService.class })
class HomeDeviceServiceQueryCountTest {

	private static final Instant REGISTERED = Instant.now().minusSeconds(3600);

	@Autowired
	HomeDeviceService homeDeviceService;

	@Autowired
	VendorRepository vendorRepository;

	@Autowired
	HomeRepository homeRepository;

	@Autowired
	DeviceRepository deviceRepository;

	@Autowired
	HomeDeviceRepository homeDeviceRepository;

	@Autowired
	EntityManager em;

	private Statistics statistics;

	@BeforeEach
	void setUp() {
		// three home devices of two catalogue devices from two vendors, all due
		Vendor samsung = vendorRepository.save(Vendor.register(VendorCode.SAMSUNG, "Samsung", REGISTERED));
		Vendor amazon = vendorRepository.save(Vendor.register(VendorCode.AMAZON, "Amazon", REGISTERED));
		Home home = homeRepository.save(Home.register("My home", "UTC", REGISTERED));
		Device fridge = deviceRepository.save(Device.register(samsung, DeviceType.REFRIGERATOR, "SS-RF-1", "Fridge",
				List.of(MetricMapping.of("temperatureMeasurement.temperature", MetricType.TEMPERATURE, Conversion.F_TO_C),
						MetricMapping.of("powerConsumptionReport.energy", MetricType.ENERGY, Conversion.WH_TO_KWH)),
				REGISTERED));
		Device ac = deviceRepository.save(Device.register(amazon, DeviceType.AC, "AZ-AC-1", "AC",
				List.of(MetricMapping.of("powerState", MetricType.SWITCH, Conversion.NONE)), REGISTERED));
		homeDeviceRepository.save(HomeDevice.register(home, fridge, "ss-rf-01", "A", 60, REGISTERED.minusSeconds(180)));
		homeDeviceRepository.save(HomeDevice.register(home, ac, "amz-ac-01", "B", 60, REGISTERED.minusSeconds(120)));
		homeDeviceRepository.save(HomeDevice.register(home, ac, "amz-ac-02", "C", 60, REGISTERED.minusSeconds(60)));
		em.flush();
		em.clear();
		statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
	}

	@AfterEach
	void tearDown() {
		statistics.setStatisticsEnabled(false);
	}

	@Test
	void shouldBuildCollectionTargets_inFourQueries_withLimitInSql() {
		// when
		List<CollectionTarget> targets = homeDeviceService.findDueTargets(2);

		// then: due home devices, their devices, their vendors, the devices' mappings (batch-fetched)
		assertThat(targets).extracting(CollectionTarget::getExternalDeviceId).containsExactly("ss-rf-01", "amz-ac-01");
		assertThat(targets).extracting(CollectionTarget::getVendorCode).containsExactly(VendorCode.SAMSUNG, VendorCode.AMAZON);
		assertThat(targets.get(0).getMappings()).hasSize(2);
		assertThat(targets.get(1).getMappings()).extracting(MetricMapping::getExternalMetric).containsExactly("powerState");
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
	}

	@Test
	void shouldListHomeDevices_inThreeQueries() {
		// when
		List<HomeDeviceResponse> listed = homeDeviceService.list(null);

		// then: home devices, their devices, their vendors
		assertThat(listed).extracting(HomeDeviceResponse::getModel).containsExactly("SS-RF-1", "AZ-AC-1", "AZ-AC-1");
		assertThat(listed).extracting(HomeDeviceResponse::getVendorCode)
				.containsExactly(VendorCode.SAMSUNG, VendorCode.AMAZON, VendorCode.AMAZON);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
	}
}
