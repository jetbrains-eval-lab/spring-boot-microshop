package shop.microservices.composite.product;

import org.springframework.boot.actuate.health.CompositeReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import shop.microservices.composite.product.services.ProductCompositeIntegration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class HealthCheckConfiguration {

    private final ProductCompositeIntegration integration;

    public HealthCheckConfiguration(ProductCompositeIntegration integration) {
        this.integration = integration;
    }

    @Bean
    ReactiveHealthContributor coreServices() {
        final Map<String, ReactiveHealthIndicator> registry = new LinkedHashMap<>();

        registry.put("product", integration::getProductHealth);
        registry.put("recommendation", integration::getRecommendationHealth);
        registry.put("review", integration::getReviewHealth);

        return CompositeReactiveHealthContributor.fromMap(registry);
    }
}
