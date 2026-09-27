package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.homedevice.CollectionTarget;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.vendor.adapter.RawMetricSample;
import com.abhishek.smarthome.vendor.adapter.VendorAdapter;
import com.abhishek.smarthome.vendor.adapter.VendorAdapterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * One collection pass: for each due home device, fetch its vendor metrics since the last run, convert them with the
 * device's mappings and store them. Vendor calls run outside any transaction; each device's readings and its
 * {@code nextRunAt} are saved together. A failing device is retried later and never stops the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricCollectionService {

	/** Vendors serve at most 24 hours per request; a device offline longer is caught up from the last 24 hours. */
	static final Duration MAX_RANGE = Duration.ofHours(24);

	private final HomeDeviceService homeDeviceService;
	private final VendorAdapterRegistry adapters;
	private final DeviceReadingConverter converter;
	private final DeviceReadingService readingService;
	private final Clock clock;

	/**
	 * Collects every device that is due now, up to {@code batchSize}.
	 *
	 * @return how many devices were collected successfully
	 */
	public int collectDue(int batchSize, Duration retryDelay) {
		List<CollectionTarget> targets = homeDeviceService.findDueTargets(batchSize);
		int succeeded = 0;
		for (CollectionTarget target : targets) {
			if (collect(target, retryDelay)) {
				succeeded++;
			}
		}
		if (!targets.isEmpty()) {
			log.info("Collected {}/{} due home devices", succeeded, targets.size());
		}
		return succeeded;
	}

	/** Collects one device; on failure schedules a retry instead of throwing. */
	boolean collect(CollectionTarget target, Duration retryDelay) {
		Instant to = clock.instant();
		Instant from = rangeStart(target, to);
		MDC.put("homeDeviceId", target.getHomeDeviceId().toString());
		MDC.put("vendor", target.getVendorCode().name());
		try {
			VendorAdapter adapter = adapters.adapterFor(target.getVendorCode());
			List<RawMetricSample> samples = adapter.fetchMetrics(target.getExternalDeviceId(), from, to);
			List<DeviceReading> readings = converter.convert(target.getHomeDeviceId(), target.getMappings(), samples,
					to);
			int stored = readingService.saveCollected(target.getHomeDeviceId(), readings, from, to);
			log.debug("Collected {} samples → {} readings ({} new) for {} [{}, {})", samples.size(), readings.size(),
					stored, target.getExternalDeviceId(), from, to);
			return true;
		}
		catch (RuntimeException e) {
			log.warn("Collection failed for home device {} ({} {}), retrying in {}: {}", target.getHomeDeviceId(),
					target.getVendorCode(), target.getExternalDeviceId(), retryDelay, e.toString());
			try {
				homeDeviceService.scheduleRetry(target.getHomeDeviceId(), retryDelay);
			}
			catch (RuntimeException retryFailure) {
				log.error("Could not schedule retry for home device {}", target.getHomeDeviceId(), retryFailure);
			}
			return false;
		}
		finally {
			MDC.remove("homeDeviceId");
			MDC.remove("vendor");
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
