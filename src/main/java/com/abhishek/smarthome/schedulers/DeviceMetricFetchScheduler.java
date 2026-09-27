package com.abhishek.smarthome.schedulers;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.service.MetricCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every second, collects metrics for home devices that are due ({@code enabled} and {@code nextRunAt <= now}): the
 * vendor adapter for each device's vendor fetches samples since {@code lastRunAt}, the device's mappings convert them
 * to {@code DeviceReading}s, and they are saved while {@code nextRunAt} moves one polling interval ahead.
 *
 * <p>{@code fixedDelay} means the next tick starts one second after the previous one finished, so ticks never
 * overlap. Assumes a single instance; add a distributed lock (e.g. ShedLock) before running several.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "smarthome.collection", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DeviceMetricFetchScheduler {

	private final MetricCollectionService metricCollectionService;
	private final CollectionProperties collectionProperties;

	@Scheduled(fixedDelayString = "PT1S", initialDelayString = "PT5S")
	public void fetchDueDeviceMetrics() {
		try {
			metricCollectionService.collectDue(collectionProperties.getBatchSize(), collectionProperties.getRetryDelay());
		}
		catch (RuntimeException e) {
			log.error("Metrics collection tick failed", e);
		}
	}
}
