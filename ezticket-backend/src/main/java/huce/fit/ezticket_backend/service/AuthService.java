package huce.fit.ezticket_backend.service;

import huce.fit.ezticket_backend.dto.auth.AuthResponse;
import huce.fit.ezticket_backend.dto.auth.LoginRequest;
import huce.fit.ezticket_backend.dto.auth.RegisterRequest;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.UserRole;
import huce.fit.ezticket_backend.entity.enums.UserStatus;
import huce.fit.ezticket_backend.repository.UserRepository;
import huce.fit.ezticket_backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    // Tiêu chuẩn 1: Đăng ký & mã hóa bcrypt
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(request.getAddress())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
    }

    // Tiêu chuẩn 2: Đăng nhập cấp Access Token và lưu Refresh Token trong HttpOnly Cookie
    public ResponseEntity<AuthResponse> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalStateException("Tài khoản của bạn đã bị khóa");
        }

        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // Đổi thành true khi deploy production HTTPS
                .path("/api/v1/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")
                .build();

        AuthResponse body = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getAccessTokenExpiration() / 1000)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    // Tiêu chuẩn 3: Endpoint /auth/refresh cấp Access Token mới
    public ResponseEntity<AuthResponse> refreshToken(String refreshToken) {
        if (refreshToken == null || !jwtUtils.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh Token không hợp lệ hoặc đã hết hạn");
        }

        String email = jwtUtils.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại"));

        String newAccessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getRole().name());

        AuthResponse body = AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getAccessTokenExpiration() / 1000)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(body);
    }
}