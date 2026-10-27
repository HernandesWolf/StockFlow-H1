package com.stockflow.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 120) String name,
        @Email @Size(max = 120) String email,
        @Size(max = 40) String phone) {
}
