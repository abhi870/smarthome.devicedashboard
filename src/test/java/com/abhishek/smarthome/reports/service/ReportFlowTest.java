package com.abhishek.smarthome.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
import com.abhishek.smarthome.reports.dto.output.DailyValueResponse;
import com.abhishek.smarthome.reports.dto.output.MetricSummaryResponse;
import com.abhishek.smarthome.reports.dto.output.ReportResponse;
import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportStatus;
import com.abhishek.smarthome.reports.enums.ReportType;
import com.abhishek.smarthome.reports.exception.UnsupportedReportMetricException;
import com.abhishek.smarthome.reports.repository.DailyMetricRollupRepository;
import com.abhishek.smarthome.reports.repository.ReportRepository;
import com.abhishek.smarthome.reports.scheduler.ReportProperties;
import com.abhishek.smarthome.repository.DeviceReadingRepository;
import com.abhishek.smarthome.repository.DeviceRepository;
import com.abhishek.smarthome.repository.HomeDeviceRepository;
import com.abhishek.smarthome.repository.HomeRepository;
import com.abhishek.smarthome.repository.VendorRepository;
import com.abhishek.smarthome.service.DeviceCatalogService;
import com.abhishek.smarthome.service.HomeDeviceService;
import com.abhishek.smarthome.service.HomeService;
import com.abhishek.smarthome.service.VendorService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * End to end over the real schema: readings every 10 minutes since local midnight 4 days ago in Kolkata; energy is a
 * counter growing 0.01 kWh per reading (1.44 kWh per full day), temperature 4 °C, the switch always on.
 */
@DataJpaTest(properties = "smarthome.reports.grace-period=0s")
@Import({ ReportService.class, DailyReportService.class, DailyReportPlanner.class, ReportGenerator.class,
		ReportStatisticsService.class, HomeDeviceService.class, HomeService.class, DeviceCatalogService.class,
		VendorService.class, ReportFlowTest.ReportPropertiesConfig.class })
class ReportFlowTest {

	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
	private static final double KWH_PER_READING = 0.01;

	@TestConfiguration
	@EnableConfigurationProperties(ReportProperties.class)
	static class ReportPropertiesConfig {
	}

	@Autowired
	DailyReportService dailyReportService;

	@Autowired
	ReportService reportService;

	@Autowired
	ReportRepository reportRepository;

	@Autowired
	DailyMetricRollupRepository dailyMetricRollupRepository;

	@Autowired
	VendorRepository vendorRepository;

	@Autowired
	HomeRepository homeRepository;

	@Autowired
	DeviceRepository deviceRepository;

	@Autowired
	HomeDeviceRepository homeDeviceRepository;

	@Autowired
	DeviceReadingRepository deviceReadingRepository;

	@Autowired
	EntityManager em;

	private LocalDate day0;
	private HomeDevice fridge;

	@BeforeEach
	void setUp() {
		day0 = LocalDate.ofInstant(Instant.now(), KOLKATA).minusDays(4);
		Instant start = day0.atStartOfDay(KOLKATA).toInstant();
		Vendor samsung = vendorRepository.save(Vendor.register(VendorCode.SAMSUNG, "Samsung", start));
		Home home = homeRepository.save(Home.register("My home", "Asia/Kolkata", start));
		Device device = deviceRepository.save(Device.register(samsung, DeviceType.REFRIGERATOR, "SS-RF-1", "Fridge",
				List.of(MetricMapping.of("energy", MetricType.ENERGY, Conversion.NONE),
						MetricMapping.of("temperature", MetricType.TEMPERATURE, Conversion.NONE),
						MetricMapping.of("switch", MetricType.SWITCH, Conversion.NONE)),
				start));
		fridge = homeDeviceRepository.save(HomeDevice.register(home, device, "ss-rf-01", "Kitchen fridge", 60, start));
		List<DeviceReading> readings = new ArrayList<>();
		Instant now = Instant.now();
		for (int k = 0; start.plusSeconds(600L * k).isBefore(now); k++) {
			Instant time = start.plusSeconds(600L * k);
			readings.add(DeviceReading.record(fridge.getId(), MetricType.ENERGY, time, String.valueOf(k * KWH_PER_READING),
					"kWh", time));
			readings.add(DeviceReading.record(fridge.getId(), MetricType.TEMPERATURE, time, "4.0", "C", time));
			readings.add(DeviceReading.record(fridge.getId(), MetricType.SWITCH, time, "ON", "on/off", time));
		}
		deviceReadingRepository.saveAll(readings);
		em.flush();
	}

	private Instant startOf(LocalDate day) {
		return day.atStartOfDay(KOLKATA).toInstant();
	}

	private static MetricSummaryResponse summary(ReportResponse report, MetricType metric) {
		return report.getSummaries().stream().filter(summary -> summary.getMetric() == metric).findFirst().orElseThrow();
	}

	@Test
	void shouldGenerateOneDailyReportPerFinishedDay_withRollups_andBeIdempotent() {
		// when: day0 (registration day) .. yesterday are finished
		int generated = dailyReportService.generateDueReports();

		// then
		assertThat(generated).isEqualTo(4);
		assertThat(dailyMetricRollupRepository.count()).isEqualTo(4 * 3);
		Report fullDay = reportRepository.findByTypeAndHomeDeviceIdAndLocalDate(ReportType.DAILY, fridge.getId(),
				day0.plusDays(1)).orElseThrow();
		ReportResponse report = ReportResponse.from(fullDay);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.SUCCEEDED);
		assertThat(report.getPeriodStart()).isEqualTo(startOf(day0.plusDays(1)));
		assertThat(summary(report, MetricType.ENERGY).getTotal()).isCloseTo(144 * KWH_PER_READING, within(1e-6));
		assertThat(summary(report, MetricType.TEMPERATURE).getAvg()).isEqualTo(4.0);
		assertThat(summary(report, MetricType.SWITCH).getTotal()).isEqualTo(1440.0);
		assertThat(report.getCoveragePct()).isEqualTo(100.0);

		// and: running again finds nothing to do
		assertThat(dailyReportService.generateDueReports()).isZero();
		assertThat(reportRepository.count()).isEqualTo(4);
	}

	@Test
	void shouldCombineRollupsForFullDays_withRawReadingsAtTheEdges() {
		// given: daily rollups exist; range = 06:00 day1 .. 06:00 day3 (48 h = 288 readings)
		dailyReportService.generateDueReports();
		Instant from = startOf(day0.plusDays(1)).plusSeconds(6 * 3600);
		Instant to = startOf(day0.plusDays(3)).plusSeconds(6 * 3600);

		// when
		ReportResponse report = reportService.createCustomRange(fridge.getId(), from, to, Set.of(MetricType.ENERGY));

		// then: 18 h of day1 + all of day2 (from its rollup) + 6 h of day3
		MetricSummaryResponse energy = summary(report, MetricType.ENERGY);
		assertThat(report.getType()).isEqualTo(ReportType.CUSTOM_RANGE);
		assertThat(energy.getTotal()).isCloseTo(288 * KWH_PER_READING, within(1e-6));
		assertThat(energy.getDaily()).extracting(DailyValueResponse::getDate)
				.containsExactly(day0.plusDays(1), day0.plusDays(2), day0.plusDays(3));
		assertThat(energy.getDaily().get(0).getValue()).isCloseTo(108 * KWH_PER_READING, within(1e-6));
		assertThat(energy.getDaily().get(1).getValue()).isCloseTo(144 * KWH_PER_READING, within(1e-6));
		assertThat(energy.getDaily().get(2).getValue()).isCloseTo(36 * KWH_PER_READING, within(1e-6));

		// and: the full day really comes from its rollup (tampered with here), the edges from raw readings
		em.createNativeQuery("update daily_metric_rollup set total_value = 100 where home_device_id = ?1"
				+ " and local_date = ?2 and metric = 'ENERGY'").setParameter(1, fridge.getId())
				.setParameter(2, day0.plusDays(2)).executeUpdate();
		em.clear();
		ReportResponse regenerated = reportService.regenerate(report.getId());
		assertThat(summary(regenerated, MetricType.ENERGY).getTotal()).isCloseTo(100 + 144 * KWH_PER_READING,
				within(1e-6));
	}

	@Test
	void shouldDefaultToAllMappedMetrics_andRejectUnmappedOnes() {
		// given
		Instant from = startOf(day0.plusDays(1));
		Instant to = from.plusSeconds(3600);

		// when / then
		assertThat(reportService.createCustomRange(fridge.getId(), from, to, null).getMetrics())
				.containsExactlyInAnyOrder(MetricType.ENERGY, MetricType.TEMPERATURE, MetricType.SWITCH);
		assertThatThrownBy(() -> reportService.createCustomRange(fridge.getId(), from, to, Set.of(MetricType.HUMIDITY)))
				.isInstanceOf(UnsupportedReportMetricException.class);
	}
}
