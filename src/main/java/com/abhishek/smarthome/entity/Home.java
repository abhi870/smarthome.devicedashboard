package com.abhishek.smarthome.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A home that owns registered {@link HomeDevice}s. {@link #timezone} defines day boundaries for its daily reports. */
@Entity
@Table(name = "home")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class Home {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	/** IANA time zone id, e.g. {@code Asia/Kolkata}. */
	@Column(nullable = false, length = 64)
	private String timezone;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	/** Devices registered in this home. Inverse side; {@link HomeDevice#getHome()} owns it. Lazy, never cascaded. */
	@OneToMany(mappedBy = "home")
	@Getter(AccessLevel.NONE)
	private List<HomeDevice> devices = new ArrayList<>();

	public static Home register(String name, String timezone, Instant now) {
		Home home = new Home();
		home.id = UUID.randomUUID();
		home.name = name;
		home.timezone = timezone;
		home.createdAt = now;
		return home;
	}

	public List<HomeDevice> getDevices() {
		return Collections.unmodifiableList(devices);
	}
}
