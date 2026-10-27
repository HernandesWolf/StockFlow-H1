package com.stockflow.web.dto;

import com.stockflow.domain.Role;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, Role role) {
}
