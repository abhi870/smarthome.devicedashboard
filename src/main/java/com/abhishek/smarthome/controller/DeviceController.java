package com.abhishek.smarthome.controller;

import com.abhishek.smarthome.dto.input.device.RegisterDeviceRequest;
import com.abhishek.smarthome.dto.input.device.UpdateMetricMappingsRequest;
import com.abhishek.smarthome.dto.output.device.DeviceResponse;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.service.DeviceCatalogService;
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

/** Catalogue of supported device models: admins register them, users list them before registering a home device. */
@RestController
@RequestMapping("/api/v1/smart-home/devices")
@RequiredArgsConstructor
class DeviceController {

	private final DeviceCatalogService deviceCatalogService;

	/** Admin: add a supported device model with its metric mappings. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	DeviceResponse register(@Valid @RequestBody RegisterDeviceRequest request) {
		return deviceCatalogService.register(request.toCommand());
	}

	/** Admin: replace all metric mappings of a supported device model. */
	@PutMapping("/{id}/mappings")
	DeviceResponse replaceMappings(@PathVariable UUID id, @Valid @RequestBody UpdateMetricMappingsRequest request) {
		return deviceCatalogService.replaceMetricMappings(id, request.toMappings());
	}

	/** Users: browse supported devices, optionally by vendor and/or type. */
	@GetMapping
	List<DeviceResponse> list(@RequestParam(required = false) @Nullable UUID vendorId,
			@RequestParam(required = false) @Nullable DeviceType deviceType) {
		return deviceCatalogService.list(vendorId, deviceType);
	}

	@GetMapping("/{id}")
	DeviceResponse get(@PathVariable UUID id) {
		return deviceCatalogService.get(id);
	}
}
