package com.abhishek.smarthome.vendor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abhishek.smarthome.vendor.Vendor;
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
		VendorConfigProvider provider = providerWith(Map.of(Vendor.SAMSUNG, samsung, Vendor.CISCO, cisco));

		// when / then
		assertThat(provider.get(Vendor.SAMSUNG)).isSameAs(samsung);
		assertThat(provider.get(Vendor.CISCO)).isSameAs(cisco);
	}

	@Test
	void shouldThrowWithActionableMessage_whenVendorNotConfigured() {
		// given
		VendorConfigProvider provider = providerWith(Map.of(Vendor.SAMSUNG, samsung));

		// when / then
		assertThatThrownBy(() -> provider.get(Vendor.AMAZON))
				.isInstanceOf(VendorNotConfiguredException.class)
				.hasMessageContaining("AMAZON")
				.hasMessageContaining("smarthome.vendors.amazon");
	}

	@Test
	void shouldListConfiguredVendorsInEnumOrder() {
		// given
		VendorConfigProvider provider = providerWith(Map.of(Vendor.CISCO, cisco, Vendor.SAMSUNG, samsung));

		// when / then
		assertThat(provider.configuredVendors()).containsExactly(Vendor.SAMSUNG, Vendor.CISCO);
		assertThat(provider.isConfigured(Vendor.CISCO)).isTrue();
		assertThat(provider.isConfigured(Vendor.AMAZON)).isFalse();
	}

	@Test
	void shouldNotReflectLaterChangesToSourceMap_andBeUnmodifiable() {
		// given
		Map<Vendor, VendorProperties> source = new HashMap<>(Map.of(Vendor.SAMSUNG, samsung));
		VendorConfigProvider provider = providerWith(source);

		// when
		source.put(Vendor.CISCO, cisco);

		// then
		assertThat(provider.isConfigured(Vendor.CISCO)).isFalse();
		assertThatThrownBy(() -> provider.configuredVendors().remove(Vendor.SAMSUNG))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	private static VendorConfigProvider providerWith(Map<Vendor, VendorProperties> vendors) {
		return new VendorConfigProvider(new SmartHomeProperties(vendors));
	}

	private static VendorProperties vendor(String baseUrl) {
		return new VendorProperties(URI.create(baseUrl), Duration.ofSeconds(1), Duration.ofSeconds(2),
				new AuthProperties(AuthType.API_KEY_HEADER, "X-API-Key", null, "key"));
	}
}
