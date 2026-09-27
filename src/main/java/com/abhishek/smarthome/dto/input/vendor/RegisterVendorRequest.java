package com.abhishek.smarthome.dto.input.vendor;

import com.abhishek.smarthome.enums.VendorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** Body of {@code POST /api/v1/smart-home/vendors/register}. */
@Getter
public final class RegisterVendorRequest {

	/** SAMSUNG | AMAZON | CISCO — links the vendor to its integration settings. */
	@NotNull
	private final VendorCode code;

	@NotBlank
	private final String name;

	@JsonCreator
	public RegisterVendorRequest(@JsonProperty("code") VendorCode code, @JsonProperty("name") String name) {
		this.code = code;
		this.name = name;
	}
}
