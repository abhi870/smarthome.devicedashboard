package com.abhishek.smarthome.entity;

import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.exception.InvalidMetricMappingException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A <b>supported device model</b> in the catalogue, created by an admin (e.g. "Amazon Smart AC 12K", model
 * {@code AZ-AC12}). Users pick one of these when registering a {@link com.abhishek.smarthome.entity.HomeDevice}.
 * Many catalogue devices belong to one {@link Vendor}. Its {@link MetricMapping}s say how the vendor's metric names
 * and units translate to canonical metrics; they are owned by the device and saved/deleted with it.
 */
@Entity
@Table(name = "device", uniqueConstraints = @UniqueConstraint(name = "uk_device_vendor_model",
		columnNames = { "vendor_id", "model" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class Device {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@Enumerated(EnumType.STRING)
	@Column(name = "device_type", nullable = false, length = 20)
	private DeviceType deviceType;

	/** The vendor's model identifier. */
	@Column(nullable = false, length = 100)
	private String model;

	/** Display name shown to users when they register a device. */
	@Column(nullable = false, length = 100)
	private String name;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "device_metric_mapping", joinColumns = @JoinColumn(name = "device_id"))
	@Getter(AccessLevel.NONE)
	private List<MetricMapping> metricMappings = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	public static Device register(Vendor vendor, DeviceType deviceType, String model, String name,
			List<MetricMapping> metricMappings, Instant now) {
		Device device = new Device();
		device.id = UUID.randomUUID();
		device.vendor = vendor;
		device.deviceType = deviceType;
		device.model = model;
		device.name = name;
		device.metricMappings.addAll(requireDistinctExternalMetrics(metricMappings));
		device.createdAt = now;
		return device;
	}

	/**
	 * Replaces all metric mappings (e.g. the vendor renamed a metric). Takes effect on the next collection.
	 *
	 * @throws InvalidMetricMappingException if two mappings use the same external metric
	 */
	public void replaceMetricMappings(List<MetricMapping> newMappings) {
		requireDistinctExternalMetrics(newMappings);
		metricMappings.clear();
		metricMappings.addAll(newMappings);
	}

	private static List<MetricMapping> requireDistinctExternalMetrics(List<MetricMapping> mappings) {
		var seen = new HashSet<String>();
		for (MetricMapping mapping : mappings) {
			if (!seen.add(mapping.getExternalMetric())) {
				throw new InvalidMetricMappingException(
						"External metric '" + mapping.getExternalMetric() + "' is mapped more than once");
			}
		}
		return mappings;
	}

	public List<MetricMapping> getMetricMappings() {
		return Collections.unmodifiableList(metricMappings);
	}
}
