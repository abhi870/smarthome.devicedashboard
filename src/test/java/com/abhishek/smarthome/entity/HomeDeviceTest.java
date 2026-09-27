package com.abhishek.smarthome.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.InvalidPollingIntervalException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomeDeviceTest {

	private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

	private final Home home = Home.register("My home", "UTC", T0);
	private final Device device = Device.register(Vendor.register(VendorCode.AMAZON, "Amazon", T0), DeviceType.AC,
			"AZ-AC12", "Amazon Smart AC", List.of(), T0);

	private HomeDevice register(int intervalSeconds) {
		return HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", intervalSeconds, T0);
	}

	@Test
	void shouldBeDueImmediatelyAfterRegistration() {
		HomeDevice homeDevice = register(300);

		assertThat(homeDevice.getNextRunAt()).isEqualTo(T0);
		assertThat(homeDevice.getLastRunAt()).isNull();
		assertThat(homeDevice.isDue(T0)).isTrue();
	}

	@Test
	void shouldScheduleNextRunOneIntervalAfterEachRun() {
		HomeDevice homeDevice = register(300);

		homeDevice.markRun(T0.plusSeconds(5));

		assertThat(homeDevice.getLastRunAt()).isEqualTo(T0.plusSeconds(5));
		assertThat(homeDevice.getNextRunAt()).isEqualTo(T0.plusSeconds(305));
		assertThat(homeDevice.isDue(T0.plusSeconds(304))).isFalse();
		assertThat(homeDevice.isDue(T0.plusSeconds(305))).isTrue();
	}

	@Test
	void shouldRescheduleFromLastRun_whenIntervalChanges() {
		HomeDevice homeDevice = register(300);
		homeDevice.markRun(T0);

		// longer interval: last run + 1 h
		homeDevice.changePollingInterval(3600, T0.plusSeconds(60));
		assertThat(homeDevice.getNextRunAt()).isEqualTo(T0.plusSeconds(3600));

		// shorter interval whose slot already passed: due now
		homeDevice.changePollingInterval(60, T0.plusSeconds(120));
		assertThat(homeDevice.getNextRunAt()).isEqualTo(T0.plusSeconds(120));
	}

	@Test
	void shouldRejectIntervalOutsideOneMinuteToOneDay() {
		assertThatThrownBy(() -> register(59)).isInstanceOf(InvalidPollingIntervalException.class)
				.hasMessageContaining("between 60 and 86400");
		assertThatThrownBy(() -> register(300).changePollingInterval(86_401, T0))
				.isInstanceOf(InvalidPollingIntervalException.class);
	}

	@Test
	void shouldRetryAfterDelay_cappedAtInterval_withoutMovingLastRun() {
		HomeDevice homeDevice = register(300);
		homeDevice.markRun(T0);

		homeDevice.scheduleRetry(T0.plusSeconds(300), java.time.Duration.ofSeconds(60));
		assertThat(homeDevice.getNextRunAt()).isEqualTo(T0.plusSeconds(360));
		assertThat(homeDevice.getLastRunAt()).isEqualTo(T0);

		homeDevice.scheduleRetry(T0.plusSeconds(360), java.time.Duration.ofHours(1));
		assertThat(homeDevice.getNextRunAt()).isEqualTo(T0.plusSeconds(660)); // capped at 300 s
	}
}
