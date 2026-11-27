package com.uav.iotbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public record UserCreateRequest(
        @NotBlank(message = "username không được để trống")
        @Size(min = 3, max = 50, message = "username từ 3 đến 50 ký tự")
        String username,
        @NotBlank(message = "password không được để trống")
        @Size(min = 6, message = "password tối thiểu 6 ký tự")
        String password,
        @NotBlank(message = "fullName không được để trống")
        String fullName,
        @Email(message = "email không đúng định dạng")
        String email,
        @NotBlank(message = "role không được để trống")
        @Pattern(regexp = "ADMIN|OPERATOR|TECHNICIAN", message = "role phải là ADMIN, OPERATOR hoặc TECHNICIAN")
        String role
) {}
