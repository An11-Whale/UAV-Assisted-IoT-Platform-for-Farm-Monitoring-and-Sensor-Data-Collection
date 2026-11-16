package com.uav.iotbackend.service;

import com.uav.iotbackend.dto.LoginRequest;
import com.uav.iotbackend.dto.AuthResponse;
import com.uav.iotbackend.entity.User;
import com.uav.iotbackend.exception.UnauthorizedException;
import com.uav.iotbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public AuthResponse login(LoginRequest req) {
        // 1. Kiểm tra tồn tại username
        User user = userRepository.findByUsername(req.username().trim())
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        // 2. Kiểm tra mật khẩu (có thể nâng cấp lên BCrypt sau)
        if (!req.password().equals(user.getPassword())) {
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        // 3. Kiểm tra trạng thái hoạt động
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Tài khoản đã bị vô hiệu hóa hoặc khóa");
        }

        // 4. Tạo token phiên đăng nhập
        String token = "TOKEN-" + UUID.randomUUID();

        return AuthResponse.of(user, token);
    }
}