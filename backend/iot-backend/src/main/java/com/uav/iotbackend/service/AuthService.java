    package com.uav.iotbackend.service;

    import com.uav.iotbackend.dto.LoginRequest;
    import com.uav.iotbackend.dto.AuthResponse;
    import com.uav.iotbackend.entity.User;
    import com.uav.iotbackend.exception.UnauthorizedException;
    import com.uav.iotbackend.repository.UserRepository;
    import com.uav.iotbackend.security.JwtUtil;
    import lombok.RequiredArgsConstructor;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.stereotype.Service;

    @Service
    @RequiredArgsConstructor
    public class AuthService {

        private final UserRepository  userRepository;
        private final PasswordEncoder passwordEncoder;   // BCrypt (inject từ SecurityConfig)
        private final JwtUtil         jwtUtil;

        public AuthResponse login(LoginRequest req) {
            // 1. Kiểm tra tồn tại username
            User user = userRepository.findByUsername(req.username().trim())
                    .orElseThrow(() -> new UnauthorizedException(
                            "Tên đăng nhập hoặc mật khẩu không chính xác"));

                   // 2. Kiểm tra mật khẩu (hỗ trợ cả chữ thường lẫn BCrypt)
        boolean isMatched = req.password().equals(user.getPassword()) 
                         || passwordEncoder.matches(req.password(), user.getPassword());

        if (!isMatched) {
            throw new UnauthorizedException(
                    "Tên đăng nhập hoặc mật khẩu không chính xác");
        }
            // 3. Kiểm tra trạng thái tài khoản
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new UnauthorizedException(
                        "Tài khoản đã bị vô hiệu hóa hoặc khóa");
            }

            // 4. Tạo JWT thực
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            return AuthResponse.of(user, token);
        }
    }