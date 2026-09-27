package com.abhishek.smarthome.vendor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.config.AuthProperties;
import com.abhishek.smarthome.vendor.config.AuthType;
import com.abhishek.smarthome.vendor.config.SmartHomeProperties;
import com.abhishek.smarthome.vendor.config.VendorConfigProvider;
import com.abhishek.smarthome.vendor.config.VendorNotConfiguredException;
import com.abhishek.smarthome.vendor.config.VendorProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorAuthRegistryTest {

	private final VendorConfigProvider config = new VendorConfigProvider(new SmartHomeProperties(Map.of(
			VendorCode.SAMSUNG, vendor("X-API-Key", null, "samsung-key"),
			VendorCode.AMAZON, vendor("Authorization", "Bearer ", "amazon-key"))));

	@Test
	void shouldBuildInterceptorForEachConfiguredVendor() {
		// when
		VendorAuthRegistry registry = new VendorAuthRegistry(List.of(new ApiKeyHeaderInterceptorFactory()), config);

		// then
		assertThat(registry.interceptorFor(VendorCode.SAMSUNG)).isInstanceOf(ApiKeyHeaderInterceptor.class)
				.hasToString("ApiKeyHeaderInterceptor[header=X-API-Key, value=****]");
		assertThat(registry.interceptorFor(VendorCode.AMAZON))
				.hasToString("ApiKeyHeaderInterceptor[header=Authorization, value=****]");
	}

	@Test
	void shouldThrowNotConfigured_whenVendorHasNoConfig() {
		// given
		VendorAuthRegistry registry = new VendorAuthRegistry(List.of(new ApiKeyHeaderInterceptorFactory()), config);

		// when / then
		assertThatThrownBy(() -> registry.interceptorFor(VendorCode.CISCO))
				.isInstanceOf(VendorNotConfiguredException.class)
				.hasMessageContaining("smarthome.vendors.cisco");
	}

	@Test
	void shouldFailFast_whenNoFactoryForConfiguredAuthType() {
		// when / then
		assertThatThrownBy(() -> new VendorAuthRegistry(List.of(), config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No auth factory for type API_KEY_HEADER")
				.hasMessageContaining("SAMSUNG");
	}

	@Test
	void shouldFailFast_whenTwoFactoriesClaimSameType() {
		// when / then
		assertThatThrownBy(() -> new VendorAuthRegistry(
				List.of(new ApiKeyHeaderInterceptorFactory(), new ApiKeyHeaderInterceptorFactory()), config))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Duplicate auth factories for type API_KEY_HEADER");
	}

	private static VendorProperties vendor(String header, String prefix, String key) {
		return new VendorProperties(URI.create("http://localhost/api/v1/x"), Duration.ofSeconds(1),
				Duration.ofSeconds(2), new AuthProperties(AuthType.API_KEY_HEADER, header, prefix, key));
	}
}
