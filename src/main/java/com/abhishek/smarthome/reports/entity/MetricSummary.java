package com.abhishek.smarthome.reports.entity;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.service.MetricStatistics;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The numbers of one metric in a report (table {@code report_metric_summary}). {@code total} depends on the metric
 * (see {@link com.abhishek.smarthome.reports.enums.AggregationKind}): kWh used for energy, minutes on for a switch,
 * the counter increase for runtime / door openings, {@code null} for gauges. Min / avg / max are {@code null} when
 * there were no readings.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class MetricSummary {

	@Enumerated(EnumType.STRING)
	@Column(name = "metric", nullable = false, length = 30)
	private MetricType metric;

	@Column(name = "unit", nullable = false, length = 20)
	private String unit;

	@Column(name = "sample_count", nullable = false)
	private long sampleCount;

	@Column(name = "min_value")
	private @Nullable Double min;

	@Column(name = "max_value")
	private @Nullable Double max;

	/** Time-weighted average. */
	@Column(name = "avg_value")
	private @Nullable Double avg;

	@Column(name = "total_value")
	private @Nullable Double total;

	/** Share of the period covered by readings, 0–100. */
	@Column(name = "coverage_pct", nullable = false)
	private double coveragePct;

	public static MetricSummary from(MetricStatistics statistics) {
		MetricSummary summary = new MetricSummary();
		summary.metric = statistics.getMetric();
		summary.unit = statistics.getMetric().getUnit();
		summary.sampleCount = statistics.getSampleCount();
		summary.min = statistics.getMin();
		summary.max = statistics.getMax();
		summary.avg = statistics.getAvg();
		summary.total = statistics.getTotal();
		summary.coveragePct = statistics.getCoveragePct();
		return summary;
	}
}
