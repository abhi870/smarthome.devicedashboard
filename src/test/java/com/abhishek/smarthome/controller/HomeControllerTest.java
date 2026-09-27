package com.abhishek.smarthome.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.exception.HomeNotFoundException;
import com.abhishek.smarthome.service.HomeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HomeController.class)
@Import(GlobalExceptionHandler.class)
class HomeControllerTest {

	private static final String REGISTER = "/api/v1/smart-home/homes/register";
	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	HomeService service;

	@Test
	void shouldReturn201WithLocationAndBody_whenRegistered() {
		// given
		Home home = Home.register("My home", "Asia/Kolkata", NOW);
		given(service.register("My home", "Asia/Kolkata")).willReturn(home);

		// when
		var response = assertThat(mvc.post().uri(REGISTER).contentType(APPLICATION_JSON)
				.content("{\"name\":\"My home\",\"timezone\":\"Asia/Kolkata\"}"));

		// then
		response.hasStatus(HttpStatus.CREATED)
				.hasHeader("Location", "http://localhost/api/v1/smart-home/homes/" + home.getId());
		response.bodyJson().extractingPath("$.timezone").isEqualTo("Asia/Kolkata");
	}

	@Test
	void shouldDefaultTimezoneToUtc_whenOmitted() {
		// given
		given(service.register("My home", "UTC")).willReturn(Home.register("My home", "UTC", NOW));

		// when / then
		assertThat(mvc.post().uri(REGISTER).contentType(APPLICATION_JSON).content("{\"name\":\"My home\"}"))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson().extractingPath("$.timezone").isEqualTo("UTC");
	}

	@Test
	void shouldReturn400_whenNameMissing() {
		assertThat(mvc.post().uri(REGISTER).contentType(APPLICATION_JSON).content("{}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
	}

	@Test
	void shouldReturn404_whenHomeUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(service.get(id)).willThrow(new HomeNotFoundException(id));

		// when / then
		assertThat(mvc.get().uri("/api/v1/smart-home/homes/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}
	@Test
	void shouldListHomes() {
		// given
		given(service.list()).willReturn(List.of(Home.register("My home", "UTC", NOW)));

		// when / then
		assertThat(mvc.get().uri("/api/v1/smart-home/homes"))
				.hasStatusOk()
				.bodyJson().extractingPath("$[0].name").isEqualTo("My home");
	}
}
