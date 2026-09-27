package com.abhishek.smarthome.controller;

import com.abhishek.smarthome.dto.input.homedevice.ChangePollingIntervalRequest;
import com.abhishek.smarthome.dto.input.homedevice.RegisterHomeDeviceRequest;
import com.abhishek.smarthome.dto.output.homedevice.HomeDeviceResponse;
import com.abhishek.smarthome.service.HomeDeviceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Users register their physical appliances (of a supported catalogue device) in a home. */
@RestController
@RequestMapping("/api/v1/smart-home/home-devices")
@RequiredArgsConstructor
class HomeDeviceController {

	private final HomeDeviceService homeDeviceService;

	@PostMapping("/register")
	ResponseEntity<HomeDeviceResponse> register(@Valid @RequestBody RegisterHomeDeviceRequest request,
			UriComponentsBuilder uriBuilder) {
		HomeDeviceResponse created = HomeDeviceResponse.from(homeDeviceService.register(request.toCommand()));
		URI location = uriBuilder.path("/api/v1/smart-home/home-devices/{id}").build(created.getId());
		return ResponseEntity.created(location).body(created);
	}

	/** Changes how often metrics are collected; the response shows the recomputed {@code nextRunAt}. */
	@PutMapping("/{id}/polling-interval")
	HomeDeviceResponse changePollingInterval(@PathVariable UUID id,
			@Valid @RequestBody ChangePollingIntervalRequest request) {
		return HomeDeviceResponse.from(homeDeviceService.changePollingInterval(id, request.getPollingIntervalSeconds()));
	}

	/** Registered home devices, optionally filtered by home. */
	@GetMapping
	List<HomeDeviceResponse> list(@RequestParam(required = false) @Nullable UUID homeId) {
		return homeDeviceService.list(homeId).stream().map(HomeDeviceResponse::from).toList();
	}

	@GetMapping("/{id}")
	HomeDeviceResponse get(@PathVariable UUID id) {
		return HomeDeviceResponse.from(homeDeviceService.get(id));
	}
}
