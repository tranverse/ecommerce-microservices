package com.example.ecommerce.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI authOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Auth Service API")
                .version("v1")
                .description("Registration, login, JWT access tokens, and refresh-token rotation"));
    }
}
