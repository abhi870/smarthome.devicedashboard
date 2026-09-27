package com.abhishek.smarthome.metrics.domain.entity;

import java.util.Locale;
import java.util.Set;

/**
 * Reads vendor on/off values into the canonical {@link MetricType#SWITCH} value: {@code 1} = on, {@code 0} = off.
 * Accepts booleans, numbers or numeric text (non-zero = on) and common words, case-insensitively
 * (Samsung {@code on/off}, Amazon {@code ON/OFF}, Cisco {@code ACTIVE/IDLE}).
 */
public final class SwitchState {

	public static final double ON = 1;
	public static final double OFF = 0;

	private static final Set<String> ON_WORDS = Set.of("on", "true", "active", "running", "open");
	private static final Set<String> OFF_WORDS = Set.of("off", "false", "idle", "inactive", "standby", "stopped",
			"closed");

	private SwitchState() {
	}

	/**
	 * @throws IllegalArgumentException if the value is not a recognised on/off value
	 */
	public static double parse(Object raw) {
		if (raw instanceof Boolean flag) {
			return flag ? ON : OFF;
		}
		if (raw instanceof Number number) {
			return number.doubleValue() != 0 ? ON : OFF;
		}
		String word = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
		if (isNumeric(word)) {
			return Double.parseDouble(word) != 0 ? ON : OFF;
		}
		if (ON_WORDS.contains(word)) {
			return ON;
		}
		if (OFF_WORDS.contains(word)) {
			return OFF;
		}
		throw new IllegalArgumentException("Not an on/off value: " + raw);
	}

	private static boolean isNumeric(String text) {
		try {
			Double.parseDouble(text);
			return true;
		}
		catch (NumberFormatException e) {
			return false;
		}
	}
}
