package com.uav.iotbackend.dto;

import com.uav.iotbackend.entity.User;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String role,
        String status,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}   
