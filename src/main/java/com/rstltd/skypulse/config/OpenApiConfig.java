package com.rstltd.skypulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI skyPulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SkyPulse API")
                        .description("GNSS slope monitoring data platform - " +
                                "aggregates meteorological, hydrological, seismic, " +
                                "and space weather data for landslide early warning")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("RST.ltd")
                                .url("https://rst.ltd")));
    }
}
