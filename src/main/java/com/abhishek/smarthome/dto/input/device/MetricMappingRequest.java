package com.abhishek.smarthome.dto.input.device;

import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.exception.InvalidMetricMappingException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * One entry of {@code mappings}. Either the full recipe:
 * <pre>{"externalMetric":"room_temp_f","metric":"TEMPERATURE","externalUnit":"F","internalUnit":"C",
 *  "factor":0.555555555555556,"offset":-17.777777777777778,"sample":{"external":212,"expected":100}}</pre>
 * or a preset: {@code {"externalMetric":"room_temp_f","metric":"TEMPERATURE","conversion":"F_TO_C"}}. With neither,
 * the vendor already sends the canonical unit (factor 1, offset 0). {@code internalUnit} defaults to the metric's
 * unit and must equal it; {@code externalUnit} defaults to {@code internalUnit}.
 */
public final class MetricMappingRequest {

	@NotBlank
	@Size(max = 100)
	private final String externalMetric;

	@NotNull
	private final MetricType metric;

	/** Preset; cannot be combined with externalUnit/factor/offset. */
	private final @Nullable Conversion conversion;

	@Size(max = 20)
	private final @Nullable String externalUnit;

	@Size(max = 20)
	private final @Nullable String internalUnit;

	private final @Nullable BigDecimal factor;

	private final @Nullable BigDecimal offset;

	/** Optional check of the recipe against a known pair. */
	@Valid
	private final @Nullable MetricSampleRequest sample;

	@JsonCreator
	public MetricMappingRequest(@JsonProperty("externalMetric") String externalMetric,
			@JsonProperty("metric") MetricType metric, @JsonProperty("conversion") @Nullable Conversion conversion,
			@JsonProperty("externalUnit") @Nullable String externalUnit,
			@JsonProperty("internalUnit") @Nullable String internalUnit,
			@JsonProperty("factor") @Nullable BigDecimal factor, @JsonProperty("offset") @Nullable BigDecimal offset,
			@JsonProperty("sample") @Nullable MetricSampleRequest sample) {
		this.externalMetric = externalMetric;
		this.metric = metric;
		this.conversion = conversion;
		this.externalUnit = externalUnit;
		this.internalUnit = internalUnit;
		this.factor = factor;
		this.offset = offset;
		this.sample = sample;
	}

	/**
	 * @throws InvalidMetricMappingException if a preset is mixed with explicit values, a rule of
	 * {@link MetricMapping} is broken, or the sample check fails
	 */
	public MetricMapping toMapping() {
		MetricMapping mapping;
		if (conversion != null) {
			if (externalUnit != null || factor != null || offset != null) {
				throw new InvalidMetricMappingException("Mapping '" + externalMetric
						+ "': use either conversion or externalUnit/factor/offset, not both");
			}
			mapping = MetricMapping.of(externalMetric, metric, conversion);
			if (internalUnit != null && !internalUnit.equals(mapping.getInternalUnit())) {
				throw new InvalidMetricMappingException("internalUnit of '" + externalMetric + "' must be '"
						+ mapping.getInternalUnit() + "' for metric " + metric + ", not '" + internalUnit + "'");
			}
		}
		else {
			String internal = internalUnit == null ? metric.unit() : internalUnit.trim();
			String external = externalUnit == null ? internal : externalUnit.trim();
			mapping = new MetricMapping(externalMetric, metric, external, internal,
					factor == null ? BigDecimal.ONE : factor, offset == null ? BigDecimal.ZERO : offset);
		}
		if (sample != null) {
			mapping.verifySample(sample.getExternal(), sample.getExpected());
		}
		return mapping;
	}

	public String getExternalMetric() {
		return externalMetric;
	}

	public MetricType getMetric() {
		return metric;
	}

	public @Nullable Conversion getConversion() {
		return conversion;
	}

	public @Nullable String getExternalUnit() {
		return externalUnit;
	}

	public @Nullable String getInternalUnit() {
		return internalUnit;
	}

	public @Nullable BigDecimal getFactor() {
		return factor;
	}

	public @Nullable BigDecimal getOffset() {
		return offset;
	}

	public @Nullable MetricSampleRequest getSample() {
		return sample;
	}
}
