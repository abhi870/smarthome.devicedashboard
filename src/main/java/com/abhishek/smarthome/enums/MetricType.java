package com.abhishek.smarthome.enums;

import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.service.SwitchState;

/**
 * Canonical metrics this backend stores and reports on, each in one fixed unit. Vendor metrics are mapped onto
 * these per catalogue device (see {@link com.abhishek.smarthome.entity.MetricMapping}).
 */
public enum MetricType {

	TEMPERATURE("C"),
	POWER("W"),
	ENERGY("kWh"),
	HUMIDITY("%"),
	RUNTIME("min"),
	DOOR_OPEN_COUNT("count"),
	/** On/off state, stored as {@code 1} (on) or {@code 0} (off); see {@link SwitchState}. */
	SWITCH("on/off");

	private final String unit;

	MetricType(String unit) {
		this.unit = unit;
	}

	/** The canonical unit every stored value of this metric is in. */
	public String getUnit() {
		return unit;
	}
}
