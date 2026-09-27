package com.abhishek.smarthome.schedulers;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.abhishek.smarthome.service.MetricCollectionService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeviceMetricFetchSchedulerTest {

	private final MetricCollectionService collectionService = mock(MetricCollectionService.class);
	private final CollectionProperties properties = new CollectionProperties(true, 25, Duration.ofSeconds(30));
	private final DeviceMetricFetchScheduler scheduler = new DeviceMetricFetchScheduler(collectionService, properties);

	@Test
	void shouldCollectDueDevicesWithConfiguredBatchAndRetryDelay() {
		scheduler.fetchDueDeviceMetrics();

		then(collectionService).should().collectDue(25, Duration.ofSeconds(30));
	}

	@Test
	void shouldNotPropagateFailures_soTheNextTickStillRuns() {
		given(collectionService.collectDue(25, Duration.ofSeconds(30))).willThrow(new IllegalStateException("db down"));

		scheduler.fetchDueDeviceMetrics(); // no exception
	}
}
