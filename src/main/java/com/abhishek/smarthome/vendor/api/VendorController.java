package com.abhishek.smarthome.vendor.api;

import com.abhishek.smarthome.vendor.api.dto.RegisterVendorRequest;
import com.abhishek.smarthome.vendor.api.dto.VendorResponse;
import com.abhishek.smarthome.vendor.domain.service.VendorService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/smart-home/vendors")
class VendorController {

	private final VendorService service;

	VendorController(VendorService service) {
		this.service = service;
	}

	@PostMapping("/register")
	ResponseEntity<VendorResponse> register(@Valid @RequestBody RegisterVendorRequest request,
			UriComponentsBuilder uriBuilder) {
		VendorResponse created = VendorResponse.from(service.register(request.getCode(), request.getName()));
		URI location = uriBuilder.path("/api/v1/smart-home/vendors/{id}").build(created.getId());
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	List<VendorResponse> list() {
		return service.list().stream().map(VendorResponse::from).toList();
	}

	@GetMapping("/{id}")
	VendorResponse get(@PathVariable UUID id) {
		return VendorResponse.from(service.get(id));
	}
}
