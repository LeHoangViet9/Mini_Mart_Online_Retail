package vn.minimart.config;

import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ServiceHttpConfig {
    @Bean
    public RestClient catalogRestClient(RestClient.Builder builder,
            @Value("${CATALOG_URL:http://catalog-service:8080}") String url) {
        return client(builder, url);
    }

    @Bean
    public RestClient customerRestClient(RestClient.Builder builder,
            @Value("${CUSTOMER_URL:http://customer-service:8080}") String url) {
        return client(builder, url);
    }

    @Bean
    public RestClient shippingRestClient(RestClient.Builder builder,
            @Value("${SHIPPING_URL:http://shipping-service:8080}") String url) {
        return client(builder, url);
    }

    private RestClient client(RestClient.Builder builder, String url) {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return builder.clone().requestFactory(factory).baseUrl(url)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("X-Trace-Id", MDC.get("trace.id"));
                    return execution.execute(request, body);
                }).build();
    }
}
