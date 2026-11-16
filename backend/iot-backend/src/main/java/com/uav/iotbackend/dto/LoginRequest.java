package com.uav.iotbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username không được để trống")
        @Size(min = 3, max = 50, message = "username từ 3 đến 50 ký tự")
        String username,

        @NotBlank(message = "password không được để trống")
        @Size(min = 6, message = "password tối thiểu 6 ký tự")
        String password
) {}