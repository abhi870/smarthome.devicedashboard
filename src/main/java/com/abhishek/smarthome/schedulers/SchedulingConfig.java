package com.abhishek.smarthome.schedulers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs unless {@code smarthome.collection.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "smarthome.collection", name = "enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
