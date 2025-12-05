package com.example.salesforce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SalesforceConfig {

    @Value("${salesforce.instance.url:}")
    private String instanceUrl;

    @Bean
    public WebClient salesforceWebClient() {
        return WebClient.builder()
                .baseUrl(instanceUrl)
                .build();
    }
}








