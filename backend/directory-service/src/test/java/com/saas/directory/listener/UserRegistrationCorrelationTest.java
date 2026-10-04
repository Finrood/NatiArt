package com.saas.directory.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.saas.directory.configuration.RequestCorrelationFilter;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.service.AsaasProvisioningService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
class UserRegistrationCorrelationTest {
    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private AsaasProvisioningService provisioningService;

    @Test
    void committedRegistrationCarriesRequestIdThroughActualAsyncWorker() throws Exception {
        final CountDownLatch called = new CountDownLatch(1);
        final AtomicReference<String> workerRequestId = new AtomicReference<>();
        doAnswer(invocation -> {
                    workerRequestId.set(MDC.get(RequestCorrelationFilter.MDC_KEY));
                    called.countDown();
                    return null;
                })
                .when(provisioningService)
                .provisionUser(eq("synthetic@example.invalid"));

        final Logger logger = (Logger) LoggerFactory.getLogger(UserRegistrationListener.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(status ->
                            events.publishEvent(new UserRegisteredEvent("synthetic@example.invalid", "signup-42")));
            assertTrue(called.await(5, TimeUnit.SECONDS));
            assertEquals("signup-42", workerRequestId.get());
            final List<ILoggingEvent> recorded = List.copyOf(appender.list);
            assertTrue(recorded.stream()
                    .anyMatch(event ->
                            "signup-42".equals(event.getMDCPropertyMap().get(RequestCorrelationFilter.MDC_KEY))));
            assertFalse(recorded.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("synthetic@example.invalid")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void asyncProviderFailureLogsRequestIdWithoutExceptionPayload() throws Exception {
        final String privatePayload =
                "cpf=12345678909 email=synthetic@example.invalid\nFORGED_LOG " + "x".repeat(20_000);
        doAnswer(invocation -> {
                    throw new IllegalStateException(privatePayload);
                })
                .when(provisioningService)
                .provisionUser(eq("failure@example.invalid"));

        final CountDownLatch logged = new CountDownLatch(1);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                super.append(event);
                if (event.getFormattedMessage().contains("Provisioning wake-up failed")) {
                    logged.countDown();
                }
            }
        };
        appender.start();
        final Logger logger = (Logger) LoggerFactory.getLogger(UserRegistrationListener.class);
        logger.addAppender(appender);
        try {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(status ->
                            events.publishEvent(new UserRegisteredEvent("failure@example.invalid", "signup-43")));
            assertTrue(logged.await(5, TimeUnit.SECONDS));
            final ILoggingEvent failure = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains("Provisioning wake-up failed"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("signup-43", failure.getMDCPropertyMap().get(RequestCorrelationFilter.MDC_KEY));
            final String message = failure.getFormattedMessage();
            assertTrue(message.contains("requestId=signup-43"));
            assertFalse(message.contains("12345678909"));
            assertFalse(message.contains("synthetic@example.invalid"));
            assertFalse(message.contains("FORGED_LOG"));
            assertTrue(message.length() < 200);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
