package com.abhishek.smarthome.controller;

import com.abhishek.smarthome.dto.input.home.RegisterHomeRequest;
import com.abhishek.smarthome.dto.output.home.HomeResponse;
import com.abhishek.smarthome.service.HomeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/smart-home/homes")
@RequiredArgsConstructor
class HomeController {

	private final HomeService homeService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	HomeResponse register(@Valid @RequestBody RegisterHomeRequest request) {
		return homeService.register(request.getName(), request.getTimezoneOrDefault());
	}

	@GetMapping
	List<HomeResponse> list() {
		return homeService.list();
	}

	@GetMapping("/{id}")
	HomeResponse get(@PathVariable UUID id) {
		return homeService.get(id);
	}
}
