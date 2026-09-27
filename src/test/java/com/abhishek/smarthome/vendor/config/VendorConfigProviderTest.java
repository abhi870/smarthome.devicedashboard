package com.abhishek.smarthome.vendor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abhishek.smarthome.enums.AuthType;
import com.abhishek.smarthome.enums.VendorCode;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorConfigProviderTest {

	private final VendorProperties samsung = vendor("http://localhost/api/v1/samsung");
	private final VendorProperties cisco = vendor("http://localhost/api/v1/cisco");

	@Test
	void shouldReturnConfig_whenVendorConfigured() {
		// given
		VendorConfigProvider vendorConfigProvider = providerWith(Map.of(VendorCode.SAMSUNG, samsung, VendorCode.CISCO, cisco));

		// when / then
		assertThat(vendorConfigProvider.get(VendorCode.SAMSUNG)).isSameAs(samsung);
		assertThat(vendorConfigProvider.get(VendorCode.CISCO)).isSameAs(cisco);
	}

	@Test
	void shouldThrowWithActionableMessage_whenVendorNotConfigured() {
		// given
		VendorConfigProvider vendorConfigProvider = providerWith(Map.of(VendorCode.SAMSUNG, samsung));

		// when / then
		assertThatThrownBy(() -> vendorConfigProvider.get(VendorCode.AMAZON))
				.isInstanceOf(VendorNotConfiguredException.class)
				.hasMessageContaining("AMAZON")
				.hasMessageContaining("smarthome.vendors.amazon");
	}

	@Test
	void shouldListConfiguredVendorsInEnumOrder() {
		// given
		VendorConfigProvider vendorConfigProvider = providerWith(Map.of(VendorCode.CISCO, cisco, VendorCode.SAMSUNG, samsung));

		// when / then
		assertThat(vendorConfigProvider.configuredVendors()).containsExactly(VendorCode.SAMSUNG, VendorCode.CISCO);
		assertThat(vendorConfigProvider.isConfigured(VendorCode.CISCO)).isTrue();
		assertThat(vendorConfigProvider.isConfigured(VendorCode.AMAZON)).isFalse();
	}

	@Test
	void shouldNotReflectLaterChangesToSourceMap_andBeUnmodifiable() {
		// given
		Map<VendorCode, VendorProperties> source = new HashMap<>(Map.of(VendorCode.SAMSUNG, samsung));
		VendorConfigProvider vendorConfigProvider = providerWith(source);

		// when
		source.put(VendorCode.CISCO, cisco);

		// then
		assertThat(vendorConfigProvider.isConfigured(VendorCode.CISCO)).isFalse();
		assertThatThrownBy(() -> vendorConfigProvider.configuredVendors().remove(VendorCode.SAMSUNG))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	private static VendorConfigProvider providerWith(Map<VendorCode, VendorProperties> vendors) {
		return new VendorConfigProvider(new SmartHomeProperties(vendors));
	}

	private static VendorProperties vendor(String baseUrl) {
		return new VendorProperties(URI.create(baseUrl), Duration.ofSeconds(1), Duration.ofSeconds(2),
				new AuthProperties(AuthType.API_KEY_HEADER, "X-API-Key", null, "key"));
	}
}
