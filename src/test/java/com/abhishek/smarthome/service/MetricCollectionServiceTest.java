package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.abhishek.smarthome.dto.input.homedevice.CollectionTarget;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.adapter.RawMetricSample;
import com.abhishek.smarthome.vendor.adapter.VendorAdapter;
import com.abhishek.smarthome.vendor.adapter.VendorAdapterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class MetricCollectionServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:05:30Z");
	private static final Duration RETRY = Duration.ofSeconds(60);

	private final HomeDeviceService homeDeviceService = mock(HomeDeviceService.class);
	private final VendorAdapterRegistry adapters = mock(VendorAdapterRegistry.class);
	private final VendorAdapter amazon = mock(VendorAdapter.class);
	private final DeviceReadingService readingService = mock(DeviceReadingService.class);
	private final MetricCollectionService service = new MetricCollectionService(homeDeviceService, adapters,
			new DeviceReadingConverter(), readingService, Clock.fixed(NOW, ZoneOffset.UTC));

	private final List<MetricMapping> mappings = List.of(
			MetricMapping.of("powerState", MetricType.SWITCH, Conversion.NONE),
			MetricMapping.of("powerConsumption", MetricType.POWER, Conversion.KW_TO_W));

	private CollectionTarget target(Instant lastRunAt) {
		return new CollectionTarget(UUID.randomUUID(), VendorCode.AMAZON, "amz-ac-01", 300, lastRunAt, mappings);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldFetchSinceLastRun_convert_andSave() {
		// given
		CollectionTarget target = target(NOW.minusSeconds(300));
		given(homeDeviceService.findDueTargets(50)).willReturn(List.of(target));
		given(adapters.adapterFor(VendorCode.AMAZON)).willReturn(amazon);
		Instant minute = Instant.parse("2026-09-27T10:01:00Z");
		given(amazon.fetchMetrics("amz-ac-01", NOW.minusSeconds(300), NOW)).willReturn(List.of(
				new RawMetricSample(minute, Map.of("powerState", "ON", "powerConsumption", 1.15))));

		// when
		int succeeded = service.collectDue(50, RETRY);

		// then
		assertThat(succeeded).isEqualTo(1);
		ArgumentCaptor<List<DeviceReading>> readings = ArgumentCaptor.forClass(List.class);
		then(readingService).should().saveCollected(eq(target.getHomeDeviceId()), readings.capture(),
				eq(NOW.minusSeconds(300)), eq(NOW));
		assertThat(readings.getValue()).extracting(DeviceReading::getValue).containsExactly("ON", "1150");
		then(homeDeviceService).should(never()).scheduleRetry(any(), any());
	}

	@Test
	void shouldLookBackOneInterval_onFirstRun_andAtMost24Hours() {
		assertThat(MetricCollectionService.rangeStart(target(null), NOW)).isEqualTo(NOW.minusSeconds(300));
		assertThat(MetricCollectionService.rangeStart(target(NOW.minus(Duration.ofDays(3))), NOW))
				.isEqualTo(NOW.minus(Duration.ofHours(24)));
	}

	@Test
	void shouldScheduleRetry_andContinueWithOtherDevices_whenVendorFails() {
		// given: first device's vendor is down, second succeeds
		CollectionTarget failing = target(NOW.minusSeconds(300));
		CollectionTarget ok = target(NOW.minusSeconds(300));
		given(homeDeviceService.findDueTargets(50)).willReturn(List.of(failing, ok));
		given(adapters.adapterFor(VendorCode.AMAZON)).willReturn(amazon);
		given(amazon.fetchMetrics(any(), any(), any()))
				.willThrow(new ResourceAccessException("connection refused"))
				.willReturn(List.of());

		// when
		int succeeded = service.collectDue(50, RETRY);

		// then
		assertThat(succeeded).isEqualTo(1);
		then(homeDeviceService).should().scheduleRetry(failing.getHomeDeviceId(), RETRY);
		then(readingService).should().saveCollected(eq(ok.getHomeDeviceId()), anyList(), any(), eq(NOW));
		then(readingService).should(never()).saveCollected(eq(failing.getHomeDeviceId()), anyList(), any(), any());
	}

	@Test
	void shouldDoNothing_whenNoDeviceIsDue() {
		given(homeDeviceService.findDueTargets(50)).willReturn(List.of());

		assertThat(service.collectDue(50, RETRY)).isZero();
		then(adapters).shouldHaveNoInteractions();
	}
}
