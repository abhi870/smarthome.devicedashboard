package com.abhishek.smarthome.dto.output.vendor;

import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class VendorResponse {

	private final UUID id;
	private final VendorCode code;
	private final String name;
	private final Instant createdAt;

	private VendorResponse(Vendor v) {
		this.id = v.getId();
		this.code = v.getCode();
		this.name = v.getName();
		this.createdAt = v.getCreatedAt();
	}

	public static VendorResponse from(Vendor vendor) {
		return new VendorResponse(vendor);
	}
}
