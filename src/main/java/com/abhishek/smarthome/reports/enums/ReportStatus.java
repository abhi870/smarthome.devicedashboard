package com.abhishek.smarthome.reports.enums;

/** Lifecycle of a report. Only {@code SUCCEEDED} reports carry summaries. */
public enum ReportStatus {

	QUEUED,
	RUNNING,
	SUCCEEDED,

	/** Generation failed; DAILY reports are retried at {@code nextAttemptAt} until the attempt limit. */
	FAILED
}
