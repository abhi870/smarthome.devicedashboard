package com.abhishek.smarthome.schedulers;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs. Each job switches itself on/off with its own property
 * ({@code smarthome.collection.enabled}, {@code smarthome.reports.enabled}).
 */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
