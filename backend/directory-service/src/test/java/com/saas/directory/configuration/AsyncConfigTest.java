package com.saas.directory.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootTest
class AsyncConfigTest {

    @Autowired
    @Qualifier("taskExecutor")
    private TaskExecutor taskExecutor;

    @Test
    void exposesBoundedTaskExecutorForAsyncWork() {
        assertTrue(
                taskExecutor instanceof ThreadPoolTaskExecutor,
                "the async executor must be a fixed thread pool, not a one-thread-per-task executor");
        final ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) taskExecutor;
        assertEquals(2, pool.getCorePoolSize());
        assertEquals(4, pool.getMaxPoolSize());
        assertEquals(100, pool.getQueueCapacity());
        assertTrue(
                pool.getThreadPoolExecutor().getRejectedExecutionHandler()
                        instanceof ThreadPoolExecutor.CallerRunsPolicy,
                "overflow must run on the caller thread instead of dropping registration work");
    }
}
