package com.crewmeister.cmcodingchallenge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class Config {

    @Bean
    RestClient bundesbankRestClient(@Value("${bundesbank.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}