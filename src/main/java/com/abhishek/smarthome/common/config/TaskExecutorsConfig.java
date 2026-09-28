package com.abhishek.smarthome.common.config;

import com.abhishek.smarthome.reports.scheduler.ReportProperties;
import com.abhishek.smarthome.schedulers.CollectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded thread pools the scheduled jobs fan their work out to. Each job waits for its tasks before its tick ends,
 * so the queue never holds more than one batch and a device (or device-day) is never worked on twice at once.
 * The {@code @Scheduled} ticks themselves run on the separate scheduler pool ({@code spring.task.scheduling}).
 */
@Configuration(proxyBeanMethods = false)
public class TaskExecutorsConfig {

	/** Collects due home devices in parallel ({@code smarthome.collection.parallelism} threads). */
	public static final String COLLECTION_EXECUTOR = "collectionExecutor";

	/** Generates due DAILY reports in parallel ({@code smarthome.reports.parallelism} threads). */
	public static final String REPORT_EXECUTOR = "reportExecutor";

	@Bean(name = COLLECTION_EXECUTOR)
	ThreadPoolTaskExecutor collectionExecutor(CollectionProperties collectionProperties) {
		return boundedPool("collect-", collectionProperties.getParallelism(), collectionProperties.getBatchSize());
	}

	@Bean(name = REPORT_EXECUTOR)
	ThreadPoolTaskExecutor reportExecutor(ReportProperties reportProperties) {
		return boundedPool("report-", reportProperties.getParallelism(), reportProperties.getBatchSize());
	}

	/** Fixed number of threads, a queue as large as one batch; finishes running tasks on shutdown. */
	private static ThreadPoolTaskExecutor boundedPool(String threadNamePrefix, int threads, int queueCapacity) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix(threadNamePrefix);
		executor.setCorePoolSize(threads);
		executor.setMaxPoolSize(threads);
		executor.setQueueCapacity(queueCapacity);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}
}
