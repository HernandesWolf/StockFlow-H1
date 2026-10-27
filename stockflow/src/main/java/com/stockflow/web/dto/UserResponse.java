package com.stockflow.web.dto;

import com.stockflow.domain.AppUser;
import com.stockflow.domain.Role;

import java.time.Instant;

public record UserResponse(Long id, String username, Role role, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
