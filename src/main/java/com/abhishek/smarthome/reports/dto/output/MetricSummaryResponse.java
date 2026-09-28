package com.abhishek.smarthome.reports.dto.output;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.entity.MetricSummary;
import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** The numbers of one metric; {@code daily} is empty for DAILY reports. */
@Getter
public final class MetricSummaryResponse {

	private final MetricType metric;
	private final String unit;
	private final long sampleCount;
	private final @Nullable Double min;
	private final @Nullable Double max;
	private final @Nullable Double avg;
	private final @Nullable Double total;
	private final double coveragePct;
	private final List<DailyValueResponse> daily;

	private MetricSummaryResponse(MetricSummary summary, List<DailyValueResponse> daily) {
		this.metric = summary.getMetric();
		this.unit = summary.getUnit();
		this.sampleCount = summary.getSampleCount();
		this.min = summary.getMin();
		this.max = summary.getMax();
		this.avg = summary.getAvg();
		this.total = summary.getTotal();
		this.coveragePct = summary.getCoveragePct();
		this.daily = List.copyOf(daily);
	}

	public static MetricSummaryResponse from(MetricSummary summary, List<DailyValueResponse> daily) {
		return new MetricSummaryResponse(summary, daily);
	}
}
