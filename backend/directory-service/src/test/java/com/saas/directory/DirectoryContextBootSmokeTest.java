package com.saas.directory;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:ca53-directory-boot;DB_CLOSE_DELAY=-1",
            "spring.sql.init.mode=never",
            "natiart.payment.asaas.apikey=ca53-inert-key",
            "natiart.payment.asaas.customers-url=http://127.0.0.1:1/customers"
        })
@ActiveProfiles("local-h2")
class DirectoryContextBootSmokeTest {
    @Test
    void fullServletApplicationStartsWithRealSecurityAndDatabase(ApplicationContext context) throws Exception {
        try (Connection connection = context.getBean(DataSource.class).getConnection()) {
            assertEquals("H2", connection.getMetaData().getDatabaseProductName());
        }
        assertFalse(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)
                .isEmpty());
        assertNotNull(context.getBean(com.saas.directory.repository.UserRepository.class));
    }
}
