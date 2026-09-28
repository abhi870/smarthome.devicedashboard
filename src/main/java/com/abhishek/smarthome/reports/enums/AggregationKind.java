package com.abhishek.smarthome.reports.enums;

import com.abhishek.smarthome.enums.MetricType;

/** How a metric's readings add up over a period, which decides what its report {@code total} means. */
public enum AggregationKind {

	/** A level measured at a point in time (temperature, power, humidity): min / time-weighted avg / max, no total. */
	GAUGE,

	/**
	 * A cumulative counter that only goes up (energy meter, runtime, door openings): the total is its increase over
	 * the period. A drop is treated as a counter reset and not counted.
	 */
	COUNTER,

	/** On/off state stored as 1/0: the total is the number of minutes it was on. */
	SWITCH;

	public static AggregationKind of(MetricType metric) {
		return switch (metric) {
			case TEMPERATURE, POWER, HUMIDITY -> GAUGE;
			case ENERGY, RUNTIME, DOOR_OPEN_COUNT -> COUNTER;
			case SWITCH -> SWITCH;
		};
	}
}
