package com.abhishek.smarthome.controller;

import com.abhishek.smarthome.dto.input.homedevice.ChangePollingIntervalRequest;
import com.abhishek.smarthome.dto.input.homedevice.RegisterHomeDeviceRequest;
import com.abhishek.smarthome.dto.output.homedevice.HomeDeviceResponse;
import com.abhishek.smarthome.service.HomeDeviceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users register their physical appliances (of a supported catalogue device) in a home. */
@RestController
@RequestMapping("/api/v1/smart-home/home-devices")
@RequiredArgsConstructor
class HomeDeviceController {

	private final HomeDeviceService homeDeviceService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	HomeDeviceResponse register(@Valid @RequestBody RegisterHomeDeviceRequest request) {
		return homeDeviceService.register(request.toCommand());
	}

	/** Changes how often metrics are collected; the response shows the recomputed {@code nextRunAt}. */
	@PutMapping("/{id}/polling-interval")
	HomeDeviceResponse changePollingInterval(@PathVariable UUID id,
			@Valid @RequestBody ChangePollingIntervalRequest request) {
		return homeDeviceService.changePollingInterval(id, request.getPollingIntervalSeconds());
	}

	/** Registered home devices, optionally filtered by home. */
	@GetMapping
	List<HomeDeviceResponse> list(@RequestParam(required = false) @Nullable UUID homeId) {
		return homeDeviceService.list(homeId);
	}

	@GetMapping("/{id}")
	HomeDeviceResponse get(@PathVariable UUID id) {
		return homeDeviceService.get(id);
	}
}
