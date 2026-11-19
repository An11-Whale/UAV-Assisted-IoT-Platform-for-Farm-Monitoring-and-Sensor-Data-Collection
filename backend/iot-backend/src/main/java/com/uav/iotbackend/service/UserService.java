package com.uav.iotbackend.service;

import com.uav.iotbackend.dto.UserCreateRequest;
import com.uav.iotbackend.dto.UserResponse;
import com.uav.iotbackend.entity.User;
import com.uav.iotbackend.exception.ConflictException;
import com.uav.iotbackend.exception.NotFoundException;
import com.uav.iotbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.uav.iotbackend.dto.UserUpdateRequest;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Transactional
    public UserResponse createUser(UserCreateRequest req) {
        if (userRepository.existsByUsername(req.username().trim())) {
            throw new ConflictException("Tên đăng nhập '" + req.username() + "' đã tồn tại!");
        }
        if (req.email() != null && !req.email().isBlank() && userRepository.existsByEmail(req.email().trim())) {
            throw new ConflictException("Email '" + req.email() + "' đã được sử dụng!");
        }
        User user = new User();
        user.setUsername(req.username().trim());
        user.setPassword(passwordEncoder.encode(req.password().trim())); // Tự động mã hóa mật khẩu
        user.setFullName(req.fullName().trim());
        user.setEmail(req.email() != null ? req.email().trim() : null);
        user.setRole(req.role().trim().toUpperCase());
        user.setStatus("ACTIVE");
        return UserResponse.from(userRepository.save(user));
    }
    public Page<UserResponse> listUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size, Sort.by("id").descending()))
                .map(UserResponse::from);
    }
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + id));
        return UserResponse.from(user);
    }
      @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + id));
        user.setFullName(req.fullName().trim());
        if (req.email() != null) user.setEmail(req.email().trim());
        if (req.role() != null) user.setRole(req.role());
        if (req.status() != null) user.setStatus(req.status());
        return UserResponse.from(userRepository.save(user));
    }
}
