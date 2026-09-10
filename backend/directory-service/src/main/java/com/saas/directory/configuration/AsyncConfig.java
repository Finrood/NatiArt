package com.saas.directory.configuration;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Binds all {@code @Async} work in this service to a bounded executor.
 * <p>
 * Without a dedicated {@code taskExecutor} bean, Spring Boot falls back to an
 * effectively unbounded application executor: a registration burst on the
 * Asaas fan-out ({@code UserRegistrationListener}) spawns one thread per task
 * with no queue bound. Naming the bean {@code taskExecutor} is what Spring's
 * async interceptor looks for when several {@code TaskExecutor} beans exist
 * ({@code AsyncExecutionAspectSupport#DEFAULT_TASK_EXECUTOR_BEAN_NAME}).
 * <p>
 * Overflow runs on the caller thread ({@link ThreadPoolExecutor.CallerRunsPolicy})
 * instead of dropping registrations, and the fixed pool bounds peak thread
 * usage. All settings are tunable via {@code saas.async.*} properties.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor asyncTaskExecutor(
            @Value("${saas.async.core-pool-size:2}") int corePoolSize,
            @Value("${saas.async.max-pool-size:4}") int maxPoolSize,
            @Value("${saas.async.queue-capacity:100}") int queueCapacity) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("saas-async-");
        executor.initialize();
        return executor;
    }
}
