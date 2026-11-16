package com.uav.iotbackend.dto;

import com.uav.iotbackend.entity.User;

public record AuthResponse(
        Long id,
        String username,
        String fullName,
        String role,
        String token,
        String message
) {
    public static AuthResponse of(User user, String token) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                token,
                "Đăng nhập thành công"
        );
    }
}