package com.rstltd.skypulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    // The legacy SWCB (ardswc) .ashx endpoints 302-redirect to /api/... — a client that does not
    // follow redirects gets an empty body (this, not the User-Agent, was the real cause of earlier
    // empty responses). followRedirect(true) is the fix; the browser UA is kept as harmless defense
    // in case ardswc re-introduces UA filtering.
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

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

    @Bean("swcbWebClient")
    public WebClient swcbWebClient(@Value("${skypulse.swcb.base-url}") String baseUrl) {
        // ardswc requires a browser User-Agent and 302-redirects the legacy .ashx endpoints.
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_UA)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
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
