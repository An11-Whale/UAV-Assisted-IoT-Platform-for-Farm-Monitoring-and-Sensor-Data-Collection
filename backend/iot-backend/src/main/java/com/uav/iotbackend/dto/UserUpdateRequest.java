package com.uav.iotbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserUpdateRequest(
        @NotBlank(message = "fullName không được để trống")
        String fullName,

        @Email(message = "email không đúng định dạng")
        String email,

        @Pattern(regexp = "ADMIN|OPERATOR|TECHNICIAN", message = "role phải là ADMIN, OPERATOR hoặc TECHNICIAN")
        String role,

        @Pattern(regexp = "ACTIVE|INACTIVE|LOCKED", message = "status phải là ACTIVE, INACTIVE hoặc LOCKED")
        String status
) {}