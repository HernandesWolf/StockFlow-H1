package com.stockflow.security;

import com.stockflow.domain.Role;

/** Dados extraídos de um token JWT válido. */
public record JwtPrincipal(String username, Role role) {
}
