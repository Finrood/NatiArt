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
 * Without a {@code taskExecutor} bean, Spring Boot's default application
 * executor spawns a thread per task with no queue bound, so a registration
 * burst on the Asaas fan-out is unbounded. The bean name matches the one the
 * async interceptor falls back to when several {@code TaskExecutor} beans
 * exist; overflow runs on the caller thread instead of dropping work.
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
