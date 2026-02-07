package com.crewmeister.cmcodingchallenge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class Config {

    @Bean
    RestClient bundesbankRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.statistiken.bundesbank.de/rest")
                .build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}