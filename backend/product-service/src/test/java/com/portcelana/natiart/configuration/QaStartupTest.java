package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class QaStartupTest {
    private static final String URL = "http://fixtures:8090/internal/reset-payments";

    @Test
    void run_resetsProviderOnEveryBoot() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-h2", "qa-h2");
        final RestTemplate client = new RestTemplate();
        final MockRestServiceServer server =
                MockRestServiceServer.bindTo(client).build();
        final QaStartup startup = new QaStartup(environment, URL, "qa-key", client);
        for (int boot = 0; boot < 2; boot++) {
            server.expect(requestTo(URL))
                    .andExpect(header("access_token", "qa-key"))
                    .andRespond(withSuccess());
            startup.run(null);
            server.verify();
            server.reset();
        }
    }

    @Test
    void run_rejectsProductionAndMissingLocalProfile() {
        for (final String[] profiles : new String[][] {{"qa-h2"}, {"local-h2", "qa-h2", "production"}}) {
            final MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            final QaStartup startup = new QaStartup(environment, URL, "qa-key", new RestTemplate());
            assertThrows(IllegalStateException.class, () -> startup.run(null));
        }
    }

    @Test
    void run_providerFailurePreventsReadiness() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-h2", "qa-h2");
        final RestTemplate client = new RestTemplate();
        final MockRestServiceServer server =
                MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThrows(RuntimeException.class, () -> new QaStartup(environment, URL, "qa-key", client).run(null));
        server.verify();
    }

    @Test
    void constructor_rejectsMissingOrUnexpectedResetEndpoint() {
        for (final String url :
                new String[] {"", "http://fixtures/payments", "http://user:pass@fixtures/internal/reset-payments"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new QaStartup(new MockEnvironment(), url, "qa-key", new RestTemplate()));
        }
    }
}
