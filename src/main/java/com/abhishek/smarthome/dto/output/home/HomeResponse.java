package com.abhishek.smarthome.dto.output.home;

import com.abhishek.smarthome.entity.Home;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
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
}
