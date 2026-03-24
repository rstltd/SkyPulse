package com.rstltd.skypulse.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = WebClientConfig.class)
@TestPropertySource(properties = {
        "skypulse.cwa.base-url=https://test-cwa",
        "skypulse.wra.base-url=https://test-wra",
        "skypulse.usgs.base-url=https://test-usgs",
        "skypulse.swpc.base-url=https://test-swpc"
})
class WebClientConfigTest {

    @Autowired
    @Qualifier("cwaWebClient")
    WebClient cwaWebClient;

    @Autowired
    @Qualifier("wraWebClient")
    WebClient wraWebClient;

    @Autowired
    @Qualifier("usgsWebClient")
    WebClient usgsWebClient;

    @Autowired
    @Qualifier("swpcWebClient")
    WebClient swpcWebClient;

    @Test
    void allWebClientBeansAreCreated() {
        assertNotNull(cwaWebClient);
        assertNotNull(wraWebClient);
        assertNotNull(usgsWebClient);
        assertNotNull(swpcWebClient);
    }

    @Test
    void webClientsAreDistinctInstances() {
        assertNotSame(cwaWebClient, wraWebClient);
        assertNotSame(usgsWebClient, swpcWebClient);
        assertNotSame(cwaWebClient, usgsWebClient);
    }
}
