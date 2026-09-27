package com.abhishek.smarthome.vendor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import com.abhishek.smarthome.vendor.domain.exception.VendorNotFoundException;
import com.abhishek.smarthome.vendor.domain.service.VendorService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(VendorController.class)
@Import(GlobalExceptionHandler.class)
class VendorControllerTest {

	private static final String REGISTER = "/api/v1/smart-home/vendors/register";
	private static final String BODY = "{\"code\":\"AMAZON\",\"name\":\"Amazon\"}";

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	VendorService service;

	@Test
	void shouldReturn201WithLocationAndBody_whenRegistered() {
		// given
		Vendor vendor = Vendor.register(VendorCode.AMAZON, "Amazon", Instant.parse("2026-09-27T10:00:00Z"));
		given(service.register(VendorCode.AMAZON, "Amazon")).willReturn(vendor);

		// when
		var response = assertThat(mvc.post().uri(REGISTER).contentType(APPLICATION_JSON).content(BODY));

		// then
		response.hasStatus(HttpStatus.CREATED)
				.hasHeader("Location", "http://localhost/api/v1/smart-home/vendors/" + vendor.getId());
		response.bodyJson().extractingPath("$.code").isEqualTo("AMAZON");
		response.bodyJson().extractingPath("$.name").isEqualTo("Amazon");
	}

	@Test
	void shouldReturn400_whenCodeUnknown() {
		assertThat(mvc.post().uri(REGISTER).contentType(APPLICATION_JSON)
				.content("{\"code\":\"VENDOR_B\",\"name\":\"B\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn409_whenCodeAlreadyStored() {
		// given
		given(service.register(any(), any())).willThrow(new DataIntegrityViolationException("uk_vendor_code"));

		// when / then
		assertThat(mvc.post().uri(REGISTER).contentType(APPLICATION_JSON).content(BODY))
				.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void shouldReturn404_whenVendorUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(service.get(id)).willThrow(new VendorNotFoundException(id));

		// when / then
		assertThat(mvc.get().uri("/api/v1/smart-home/vendors/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}
	@Test
	void shouldListVendors() {
		// given
		Instant now = Instant.parse("2026-09-27T10:00:00Z");
		given(service.list()).willReturn(List.of(Vendor.register(VendorCode.AMAZON, "Amazon", now),
				Vendor.register(VendorCode.SAMSUNG, "Samsung", now)));

		// when / then
		var response = assertThat(mvc.get().uri("/api/v1/smart-home/vendors"));
		response.hasStatusOk();
		response.bodyJson().extractingPath("$.length()").isEqualTo(2);
		response.bodyJson().extractingPath("$[1].code").isEqualTo("SAMSUNG");
	}
}
