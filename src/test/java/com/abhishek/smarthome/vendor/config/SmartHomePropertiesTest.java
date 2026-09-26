package com.abhishek.smarthome.vendor.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.vendor.Vendor;
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
					SmartHomeProperties props = context.getBean(SmartHomeProperties.class);
					assertThat(props.vendors()).containsOnlyKeys(Vendor.SAMSUNG, Vendor.AMAZON, Vendor.CISCO);

					VendorProperties samsung = props.vendors().get(Vendor.SAMSUNG);
					assertThat(samsung.baseUrl()).isEqualTo(URI.create("http://localhost:8080/api/v1/samsung"));
					assertThat(samsung.auth().type()).isEqualTo(AuthType.API_KEY_HEADER);
					assertThat(samsung.auth().name()).isEqualTo("X-API-Key");
					assertThat(samsung.auth().prefix()).isNull();

					AuthProperties amazon = props.vendors().get(Vendor.AMAZON).auth();
					assertThat(amazon.name()).isEqualTo("Authorization");
					assertThat(amazon.prefix()).isEqualTo("Bearer ");

					AuthProperties cisco = props.vendors().get(Vendor.CISCO).auth();
					assertThat(cisco.type()).isEqualTo(AuthType.API_KEY_QUERY);
					assertThat(cisco.name()).isEqualTo("api_key");
				});
	}

	@Test
	void shouldApplyDefaultTimeouts_whenTimeoutsNotConfigured() {
		runner.withPropertyValues(validSamsung())
				.run(context -> {
					VendorProperties samsung = context.getBean(SmartHomeProperties.class).vendors().get(Vendor.SAMSUNG);
					assertThat(samsung.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
					assertThat(samsung.readTimeout()).isEqualTo(Duration.ofSeconds(2));
				});
	}

	@Test
	void shouldUseConfiguredTimeouts_whenTimeoutsConfigured() {
		runner.withPropertyValues(validSamsung())
				.withPropertyValues(SAMSUNG + "connect-timeout=500ms", SAMSUNG + "read-timeout=5s")
				.run(context -> {
					VendorProperties samsung = context.getBean(SmartHomeProperties.class).vendors().get(Vendor.SAMSUNG);
					assertThat(samsung.connectTimeout()).isEqualTo(Duration.ofMillis(500));
					assertThat(samsung.readTimeout()).isEqualTo(Duration.ofSeconds(5));
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
