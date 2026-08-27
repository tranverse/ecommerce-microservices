package com.example.ecommerce.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI inventoryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Inventory Service API")
                .version("v1")
                .description("Stock management and concurrency-safe inventory reservations"));
    }
}
