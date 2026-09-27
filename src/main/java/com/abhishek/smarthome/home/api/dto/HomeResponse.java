package com.abhishek.smarthome.home.api.dto;

import com.abhishek.smarthome.home.domain.entity.Home;
import java.time.Instant;
import java.util.UUID;

public final class HomeResponse {

	private final UUID id;
	private final String name;
	private final String timezone;
	private final Instant createdAt;

	private HomeResponse(Home h) {
		this.id = h.getId();
		this.name = h.getName();
		this.timezone = h.getTimezone();
		this.createdAt = h.getCreatedAt();
	}

	public static HomeResponse from(Home home) {
		return new HomeResponse(home);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getTimezone() {
		return timezone;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
