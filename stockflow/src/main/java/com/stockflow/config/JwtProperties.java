package com.stockflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Lê as propriedades "stockflow.jwt.*" do application.yml. */
@ConfigurationProperties(prefix = "stockflow.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
