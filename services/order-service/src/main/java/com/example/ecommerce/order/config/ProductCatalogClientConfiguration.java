package com.example.ecommerce.order.config;

import com.example.ecommerce.order.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class ProductCatalogClientConfiguration {

    @Bean("productCatalogRestClient")
    RestClient productCatalogRestClient(RestClient.Builder builder, ProductCatalogProperties properties) {
        return configureProductCatalogClient(builder, properties).build();
    }

    RestClient.Builder configureProductCatalogClient(
            RestClient.Builder builder,
            ProductCatalogProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
                    }
                    return execution.execute(request, body);
                });
    }
}
