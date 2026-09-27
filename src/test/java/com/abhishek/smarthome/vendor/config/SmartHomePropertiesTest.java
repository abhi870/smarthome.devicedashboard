package com.abhishek.smarthome.vendor.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.enums.AuthType;
import com.abhishek.smarthome.enums.VendorCode;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SmartHomePropertiesTest {

	private static final String SAMSUNG = "smarthome.vendors.samsung.";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfig.class);

	@Test
	void shouldBindAllVendors_whenApplicationYamlDefaultsAreUsed() {
		runner.withInitializer(new ConfigDataApplicationContextInitializer())
				.run(context -> {
					// then
					assertThat(context).hasNotFailed();
					SmartHomeProperties smartHomeProperties = context.getBean(SmartHomeProperties.class);
					assertThat(smartHomeProperties.getVendors()).containsOnlyKeys(VendorCode.SAMSUNG, VendorCode.AMAZON, VendorCode.CISCO);

					VendorProperties samsung = smartHomeProperties.getVendors().get(VendorCode.SAMSUNG);
					assertThat(samsung.getBaseUrl()).isEqualTo(URI.create("http://localhost:8081/api/v1/samsung"));
					assertThat(samsung.getAuth().getType()).isEqualTo(AuthType.API_KEY_HEADER);
					assertThat(samsung.getAuth().getName()).isEqualTo("X-API-Key");
					assertThat(samsung.getAuth().getPrefix()).isNull();

					AuthProperties amazon = smartHomeProperties.getVendors().get(VendorCode.AMAZON).getAuth();
					assertThat(amazon.getName()).isEqualTo("Authorization");
					assertThat(amazon.getPrefix()).isEqualTo("Bearer ");

					AuthProperties cisco = smartHomeProperties.getVendors().get(VendorCode.CISCO).getAuth();
					assertThat(cisco.getType()).isEqualTo(AuthType.API_KEY_HEADER);
					assertThat(cisco.getName()).isEqualTo("X-Cisco-Api-Key");
					assertThat(cisco.getPrefix()).isNull();
				});
	}

	@Test
	void shouldApplyDefaultTimeouts_whenTimeoutsNotConfigured() {
		runner.withPropertyValues(validSamsung())
				.run(context -> {
					VendorProperties samsung = context.getBean(SmartHomeProperties.class).getVendors().get(VendorCode.SAMSUNG);
					assertThat(samsung.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
					assertThat(samsung.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
				});
	}

	@Test
	void shouldUseConfiguredTimeouts_whenTimeoutsConfigured() {
		runner.withPropertyValues(validSamsung())
				.withPropertyValues(SAMSUNG + "connect-timeout=500ms", SAMSUNG + "read-timeout=5s")
				.run(context -> {
					VendorProperties samsung = context.getBean(SmartHomeProperties.class).getVendors().get(VendorCode.SAMSUNG);
					assertThat(samsung.getConnectTimeout()).isEqualTo(Duration.ofMillis(500));
					assertThat(samsung.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
				});
	}

	@Test
	void shouldFailStartup_whenApiKeyMissing() {
		runner.withPropertyValues(
						SAMSUNG + "base-url=http://localhost/api/v1/samsung",
						SAMSUNG + "auth.type=API_KEY_HEADER",
						SAMSUNG + "auth.name=X-API-Key")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasStackTraceContaining("apiKey");
				});
	}

	@Test
	void shouldFailStartup_whenBaseUrlMissing() {
		runner.withPropertyValues(
						SAMSUNG + "auth.type=API_KEY_HEADER",
						SAMSUNG + "auth.name=X-API-Key",
						SAMSUNG + "auth.api-key=k")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasStackTraceContaining("baseUrl");
				});
	}

	@Test
	void shouldFailStartup_whenAuthTypeUnknown() {
		runner.withPropertyValues(validSamsung())
				.withPropertyValues(SAMSUNG + "auth.type=OAUTH_MAGIC")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasStackTraceContaining("OAUTH_MAGIC");
				});
	}

	@Test
	void shouldFailStartup_whenVendorKeyIsNotAKnownVendor() {
		runner.withPropertyValues(validSamsung())
				.withPropertyValues(
						"smarthome.vendors.smarthome.base-url=http://localhost/api/v1/smarthome",
						"smarthome.vendors.smarthome.auth.type=API_KEY_HEADER",
						"smarthome.vendors.smarthome.auth.name=X-API-Key",
						"smarthome.vendors.smarthome.auth.api-key=k")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasStackTraceContaining("smarthome");
				});
	}

	@Test
	void shouldFailStartup_whenNoVendorsConfigured() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void shouldMaskApiKey_whenAuthPropertiesPrinted() {
		// given
		AuthProperties auth = new AuthProperties(AuthType.API_KEY_HEADER, "X-API-Key", null, "super-secret");

		// when
		String printed = auth.toString();

		// then
		assertThat(printed).doesNotContain("super-secret").contains("apiKey=****");
	}

	private static String[] validSamsung() {
		return new String[] {
				SAMSUNG + "base-url=http://localhost/api/v1/samsung",
				SAMSUNG + "auth.type=API_KEY_HEADER",
				SAMSUNG + "auth.name=X-API-Key",
				SAMSUNG + "auth.api-key=samsung-test-key" };
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SmartHomeProperties.class)
	static class PropertiesConfig {
	}
}
