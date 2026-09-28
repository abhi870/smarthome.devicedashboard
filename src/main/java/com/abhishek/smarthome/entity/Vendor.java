package com.abhishek.smarthome.entity;

import com.abhishek.smarthome.enums.VendorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An appliance vendor the platform integrates with. {@link #code} links the stored vendor to its integration
 * settings ({@code smarthome.vendors.<code>}). One vendor has many supported {@link Device} models (see {@link Device#getVendorId()}).
 */
@Entity
@Table(name = "vendor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class Vendor {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, unique = true)
	private VendorCode code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	public static Vendor register(VendorCode code, String name, Instant now) {
		Vendor vendor = new Vendor();
		vendor.id = UUID.randomUUID();
		vendor.code = code;
		vendor.name = name;
		vendor.createdAt = now;
		return vendor;
	}
}
