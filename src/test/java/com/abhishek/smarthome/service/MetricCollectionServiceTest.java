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
import com.abhishek.smarthome.schedulers.CollectionProperties;
import com.abhishek.smarthome.vendor.adapter.RawMetricSample;
import com.abhishek.smarthome.vendor.adapter.VendorAdapter;
import com.abhishek.smarthome.vendor.adapter.VendorAdapterRegistry;
import java.time.Duration;
import java.time.Instant;
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
	private final VendorAdapterRegistry vendorAdapterRegistry = mock(VendorAdapterRegistry.class);
	private final VendorAdapter amazonVendorAdapter = mock(VendorAdapter.class);
	private final DeviceReadingService deviceReadingService = mock(DeviceReadingService.class);
	private final VendorCallLimiter vendorCallLimiter = new VendorCallLimiter(
			new CollectionProperties(true, 50, RETRY, 8, 2));
	/** Runs each device on the calling thread, so assertions see its effects directly. */
	private final MetricCollectionService metricCollectionService = new MetricCollectionService(homeDeviceService,
			vendorAdapterRegistry, new DeviceReadingConverter(), deviceReadingService, vendorCallLimiter, Runnable::run);

	private final List<MetricMapping> mappings = List.of(
			MetricMapping.of("powerState", MetricType.SWITCH, Conversion.NONE),
			MetricMapping.of("powerConsumption", MetricType.POWER, Conversion.KW_TO_W));

	private CollectionTarget target(Instant lastRunAt) {
		return new CollectionTarget(UUID.randomUUID(), VendorCode.AMAZON, "amz-ac-01", 300, lastRunAt, mappings);
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldFetchSinceLastRun_upToNow_convert_andSave() {
		// given
		Instant lastRunAt = Instant.now().minusSeconds(300);
		CollectionTarget target = target(lastRunAt);
		given(homeDeviceService.findDueTargets(50)).willReturn(List.of(target));
		given(vendorAdapterRegistry.adapterFor(VendorCode.AMAZON)).willReturn(amazonVendorAdapter);
		given(amazonVendorAdapter.fetchMetrics(eq("amz-ac-01"), eq(lastRunAt), any())).willReturn(List.of(
				new RawMetricSample(lastRunAt.plusSeconds(60), Map.of("powerState", "ON", "powerConsumption", 1.15))));

		// when
		Instant before = Instant.now();
		int succeeded = metricCollectionService.collectDue(50, RETRY);
		Instant after = Instant.now();

		// then: fetched [lastRunAt, now), converted, stored up to that same "now"
		assertThat(succeeded).isEqualTo(1);
		ArgumentCaptor<Instant> fetchedTo = ArgumentCaptor.forClass(Instant.class);
		then(amazonVendorAdapter).should().fetchMetrics(eq("amz-ac-01"), eq(lastRunAt), fetchedTo.capture());
		assertThat(fetchedTo.getValue()).isBetween(before, after);
		ArgumentCaptor<List<DeviceReading>> readings = ArgumentCaptor.forClass(List.class);
		then(deviceReadingService).should().saveCollected(eq(target.getHomeDeviceId()), readings.capture(),
				eq(lastRunAt), eq(fetchedTo.getValue()));
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
		CollectionTarget failing = target(Instant.now().minusSeconds(300));
		CollectionTarget ok = target(Instant.now().minusSeconds(300));
		given(homeDeviceService.findDueTargets(50)).willReturn(List.of(failing, ok));
		given(vendorAdapterRegistry.adapterFor(VendorCode.AMAZON)).willReturn(amazonVendorAdapter);
		given(amazonVendorAdapter.fetchMetrics(any(), any(), any()))
				.willThrow(new ResourceAccessException("connection refused"))
				.willReturn(List.of());

		// when
		int succeeded = metricCollectionService.collectDue(50, RETRY);

		// then
		assertThat(succeeded).isEqualTo(1);
		then(homeDeviceService).should().scheduleRetry(failing.getHomeDeviceId(), RETRY);
		then(deviceReadingService).should().saveCollected(eq(ok.getHomeDeviceId()), anyList(), any(), any());
		then(deviceReadingService).should(never()).saveCollected(eq(failing.getHomeDeviceId()), anyList(), any(), any());
	}

	@Test
	void shouldDoNothing_whenNoDeviceIsDue() {
		given(homeDeviceService.findDueTargets(50)).willReturn(List.of());

		assertThat(metricCollectionService.collectDue(50, RETRY)).isZero();
		then(vendorAdapterRegistry).shouldHaveNoInteractions();
	}

	@Test
	void shouldCollectDevicesInParallel_withAtMostTheVendorCapInFlight() throws Exception {
		// given: 6 due Amazon devices, 8 threads, but at most 2 Amazon calls at a time
		List<CollectionTarget> targets = java.util.stream.IntStream.range(0, 6).mapToObj(i -> new CollectionTarget(
				UUID.randomUUID(), VendorCode.AMAZON, "amz-" + i, 300, NOW, mappings)).toList();
		given(homeDeviceService.findDueTargets(50)).willReturn(targets);
		given(vendorAdapterRegistry.adapterFor(VendorCode.AMAZON)).willReturn(amazonVendorAdapter);
		java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger maxInFlight = new java.util.concurrent.atomic.AtomicInteger();
		given(amazonVendorAdapter.fetchMetrics(any(), any(), any())).willAnswer(invocation -> {
			maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
			Thread.sleep(100);
			inFlight.decrementAndGet();
			return List.of();
		});
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
		MetricCollectionService parallel = new MetricCollectionService(homeDeviceService, vendorAdapterRegistry,
				new DeviceReadingConverter(), deviceReadingService, vendorCallLimiter, pool);

		try {
			// when
			int collected = parallel.collectDue(50, RETRY);

			// then: all collected (collectDue waits for every device), never more than 2 Amazon calls at once
			assertThat(collected).isEqualTo(6);
			assertThat(maxInFlight.get()).isEqualTo(2);
			then(deviceReadingService).should(org.mockito.Mockito.times(6)).saveCollected(any(), anyList(), any(), any());
		}
		finally {
			pool.shutdownNow();
		}
	}
}
