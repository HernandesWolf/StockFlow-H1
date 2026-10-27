package com.stockflow.security;

import com.stockflow.config.JwtProperties;
import com.stockflow.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-with-more-than-32-characters";

    private static JwtService service(String secret, Duration expiration) {
        return new JwtService(new JwtProperties(secret, expiration));
    }

    @Test
    @DisplayName("Um token gerado é aceito e devolve usuário e perfil")
    void generatedTokenIsAccepted() {
        JwtService service = service(SECRET, Duration.ofMinutes(5));

        String token = service.generateToken("maria", Role.OPERATOR);
        Optional<JwtPrincipal> principal = service.parse(token);

        assertThat(principal).contains(new JwtPrincipal("maria", Role.OPERATOR));
    }

    @Test
    @DisplayName("Token adulterado é rejeitado")
    void tamperedTokenIsRejected() {
        JwtService service = service(SECRET, Duration.ofMinutes(5));
        String token = service.generateToken("maria", Role.OPERATOR);

        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("Token expirado é rejeitado")
    void expiredTokenIsRejected() {
        JwtService service = service(SECRET, Duration.ofSeconds(-5));

        String token = service.generateToken("maria", Role.ADMIN);

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("Token assinado com outra chave é rejeitado")
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service("another-secret-with-more-than-32-characters!", Duration.ofMinutes(5))
                .generateToken("maria", Role.ADMIN);

        assertThat(service(SECRET, Duration.ofMinutes(5)).parse(token)).isEmpty();
    }

    @Test
    @DisplayName("Texto qualquer não é um token válido")
    void garbageIsRejected() {
        assertThat(service(SECRET, Duration.ofMinutes(5)).parse("isso-nao-e-um-jwt")).isEmpty();
    }

    @Test
    @DisplayName("Segredo curto demais impede a aplicação de subir")
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> service("curto", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
