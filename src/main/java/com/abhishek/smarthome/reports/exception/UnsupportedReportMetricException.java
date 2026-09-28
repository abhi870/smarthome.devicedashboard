package com.abhishek.smarthome.reports.exception;

import com.abhishek.smarthome.common.error.BadRequestException;
import com.abhishek.smarthome.enums.MetricType;
import java.util.Set;

/** A report asked for metrics the device's catalogue model does not map (so it never has readings for them). */
public class UnsupportedReportMetricException extends BadRequestException {

	public UnsupportedReportMetricException(Set<MetricType> unsupported, Set<MetricType> supported) {
		super("Metrics " + unsupported + " are not collected for this device; supported: " + supported);
	}
}
