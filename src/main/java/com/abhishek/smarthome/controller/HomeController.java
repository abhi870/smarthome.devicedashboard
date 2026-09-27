package com.abhishek.smarthome.controller;

import com.abhishek.smarthome.dto.input.home.RegisterHomeRequest;
import com.abhishek.smarthome.dto.output.home.HomeResponse;
import com.abhishek.smarthome.service.HomeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/smart-home/homes")
@RequiredArgsConstructor
class HomeController {

	private final HomeService homeService;

	@PostMapping("/register")
	ResponseEntity<HomeResponse> register(@Valid @RequestBody RegisterHomeRequest request,
			UriComponentsBuilder uriBuilder) {
		HomeResponse created = HomeResponse.from(homeService.register(request.getName(), request.getTimezoneOrDefault()));
		URI location = uriBuilder.path("/api/v1/smart-home/homes/{id}").build(created.getId());
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	List<HomeResponse> list() {
		return homeService.list().stream().map(HomeResponse::from).toList();
	}

	@GetMapping("/{id}")
	HomeResponse get(@PathVariable UUID id) {
		return HomeResponse.from(homeService.get(id));
	}
}
