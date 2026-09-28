package com.abhishek.smarthome.service;

import com.abhishek.smarthome.common.config.TaskExecutorsConfig;
import com.abhishek.smarthome.dto.input.homedevice.CollectionTarget;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.vendor.adapter.RawMetricSample;
import com.abhishek.smarthome.vendor.adapter.VendorAdapterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * One collection pass: for each due home device, fetch its vendor metrics since the last run, convert them with the
 * device's mappings and store them. Vendor calls run outside any transaction; each device's readings and its
 * {@code nextRunAt} are saved together. A failing device is retried later and never stops the others.
 *
 * <p>Devices are collected in parallel on the {@link TaskExecutorsConfig#COLLECTION_EXECUTOR} pool, at most
 * {@code max-concurrent-per-vendor} calls per vendor at a time ({@link VendorCallLimiter}). Each device is
 * independent (own snapshot, own vendor call, own transaction), and a pass waits for all its devices before the
 * next tick, so no device is collected twice at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricCollectionService {

	/** Vendors serve at most 24 hours per request; a device offline longer is caught up from the last 24 hours. */
	static final Duration MAX_RANGE = Duration.ofHours(24);

	private final HomeDeviceService homeDeviceService;
	private final VendorAdapterRegistry vendorAdapterRegistry;
	private final DeviceReadingConverter deviceReadingConverter;
	private final DeviceReadingService deviceReadingService;
	private final VendorCallLimiter vendorCallLimiter;
	@Qualifier(TaskExecutorsConfig.COLLECTION_EXECUTOR)
	private final Executor collectionExecutor;

	/**
	 * Collects every device that is due now, up to {@code batchSize}, in parallel; returns when all are done.
	 *
	 * @return how many devices were collected successfully
	 */
	public int collectDue(int batchSize, Duration retryDelay) {
		List<CollectionTarget> targets = homeDeviceService.findDueTargets(batchSize);
		List<CompletableFuture<Boolean>> runs = targets.stream()
				.map(target -> CompletableFuture.supplyAsync(() -> collect(target, retryDelay), collectionExecutor))
				.toList();
		long succeeded = runs.stream().filter(CompletableFuture::join).count();
		if (!targets.isEmpty()) {
			log.info("Collected {}/{} due home devices", succeeded, targets.size());
		}
		return (int) succeeded;
	}

	/** Collects one device; on failure schedules a retry instead of throwing. */
	boolean collect(CollectionTarget target, Duration retryDelay) {
		MDC.put("homeDeviceId", target.getHomeDeviceId().toString());
		MDC.put("vendor", target.getVendorCode().name());
		try {
			Instant to = Instant.now();
			fetchAndStore(target, rangeStart(target, to), to);
			return true;
		}
		catch (RuntimeException e) {
			log.warn("Collection failed for {} {}, retrying in {}: {}", target.getVendorCode(),
					target.getExternalDeviceId(), retryDelay, e.toString());
			scheduleRetry(target, retryDelay);
			return false;
		}
		finally {
			MDC.remove("homeDeviceId");
			MDC.remove("vendor");
		}
	}

	/** Vendor call (no transaction) → readings → one transaction that stores them and moves {@code nextRunAt}. */
	private void fetchAndStore(CollectionTarget target, Instant from, Instant to) {
		List<RawMetricSample> samples = vendorCallLimiter.call(target.getVendorCode(), () -> vendorAdapterRegistry
				.adapterFor(target.getVendorCode()).fetchMetrics(target.getExternalDeviceId(), from, to));
		List<DeviceReading> readings = deviceReadingConverter.convert(target.getHomeDeviceId(), target.getMappings(), samples, to);
		int stored = deviceReadingService.saveCollected(target.getHomeDeviceId(), readings, from, to);
		log.debug("{} samples -> {} readings ({} new) for [{}, {})", samples.size(), readings.size(), stored, from, to);
	}

	private void scheduleRetry(CollectionTarget target, Duration retryDelay) {
		try {
			homeDeviceService.scheduleRetry(target.getHomeDeviceId(), retryDelay);
		}
		catch (RuntimeException e) {
			log.error("Could not schedule retry for home device {}", target.getHomeDeviceId(), e);
		}
	}

	/** Since the last run; first run looks back one polling interval; never more than {@link #MAX_RANGE}. */
	static Instant rangeStart(CollectionTarget target, Instant to) {
		Instant from = target.getLastRunAt() != null ? target.getLastRunAt()
				: to.minusSeconds(target.getPollingIntervalSeconds());
		Instant earliest = to.minus(MAX_RANGE);
		return from.isBefore(earliest) ? earliest : from;
	}
}
