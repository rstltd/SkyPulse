package com.rstltd.skypulse.collector.cwa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {
        CwaApiClient.class,
        com.rstltd.skypulse.config.WebClientConfig.class
})
@TestPropertySource(properties = {
        "skypulse.cwa.base-url=https://opendata.cwa.gov.tw/api/v1/rest/datastore",
        "skypulse.cwa.api-key=test-key"
})
class CwaApiClientTest {

    @Autowired
    CwaApiClient cwaApiClient;

    @Test
    void beanIsCreated() {
        assertNotNull(cwaApiClient);
    }

    @Test
    void getDataset_returnsMonoNotNull() {
        // Verify the method returns a Mono (does not execute the request)
        var mono = cwaApiClient.getDataset("O-A0002-001", String.class);
        assertNotNull(mono);
    }
}
