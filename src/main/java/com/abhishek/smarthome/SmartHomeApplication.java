package com.abhishek.smarthome;

import java.time.ZoneOffset;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** All stored and exchanged timestamps are UTC {@link java.time.Instant}s; see CLAUDE.md. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartHomeApplication {

	public static void main(String[] args) {
		// Belt and braces: every date/time the app creates or formats is UTC, independent of the host's timezone.
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
		SpringApplication.run(SmartHomeApplication.class, args);
	}

}
