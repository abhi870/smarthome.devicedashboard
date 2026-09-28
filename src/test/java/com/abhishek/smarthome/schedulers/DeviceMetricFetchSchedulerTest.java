package com.abhishek.smarthome.schedulers;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.abhishek.smarthome.service.MetricCollectionService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeviceMetricFetchSchedulerTest {

	private final MetricCollectionService metricCollectionService = mock(MetricCollectionService.class);
	private final CollectionProperties collectionProperties = new CollectionProperties(true, 25, Duration.ofSeconds(30), 8, 4);
	private final DeviceMetricFetchScheduler deviceMetricFetchScheduler = new DeviceMetricFetchScheduler(metricCollectionService, collectionProperties);

	@Test
	void shouldCollectDueDevicesWithConfiguredBatchAndRetryDelay() {
		deviceMetricFetchScheduler.fetchDueDeviceMetrics();

		then(metricCollectionService).should().collectDue(25, Duration.ofSeconds(30));
	}

	@Test
	void shouldNotPropagateFailures_soTheNextTickStillRuns() {
		given(metricCollectionService.collectDue(25, Duration.ofSeconds(30))).willThrow(new IllegalStateException("db down"));

		deviceMetricFetchScheduler.fetchDueDeviceMetrics(); // no exception
	}
}
