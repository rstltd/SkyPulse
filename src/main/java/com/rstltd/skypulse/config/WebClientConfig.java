package com.rstltd.skypulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("cwaWebClient")
    public WebClient cwaWebClient(@Value("${skypulse.cwa.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    @Bean("wraWebClient")
    public WebClient wraWebClient(@Value("${skypulse.wra.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    @Bean("usgsWebClient")
    public WebClient usgsWebClient(@Value("${skypulse.usgs.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("swpcWebClient")
    public WebClient swpcWebClient(@Value("${skypulse.swpc.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("omniWebWebClient")
    public WebClient omniWebWebClient() {
        return WebClient.builder()
                .baseUrl("https://omniweb.gsfc.nasa.gov")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }
}
