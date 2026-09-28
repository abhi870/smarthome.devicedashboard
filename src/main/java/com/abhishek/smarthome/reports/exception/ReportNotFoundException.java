package com.abhishek.smarthome.reports.exception;

import com.abhishek.smarthome.common.error.NotFoundException;
import java.util.UUID;

public class ReportNotFoundException extends NotFoundException {

	public ReportNotFoundException(UUID id) {
		super("Report " + id + " not found");
	}
}
