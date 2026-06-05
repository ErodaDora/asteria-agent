package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.User;
import com.dora.jagent.model.request.LoginRequest;
import com.dora.jagent.model.request.RefreshTokenRequest;
import com.dora.jagent.model.request.RegisterRequest;
import com.dora.jagent.model.request.SendRegisterCodeRequest;
import com.dora.jagent.model.response.LoginResponse;
import com.dora.jagent.model.response.ProfileResponse;
import com.dora.jagent.model.response.RegisterResponse;
import com.dora.jagent.model.response.TokenPairResponse;
import com.dora.jagent.repository.UserRepository;
import com.dora.jagent.service.AuthService;
import com.dora.jagent.service.EmailService;
import com.dora.jagent.service.VerificationCodeService;
import com.dora.jagent.util.JwtUtils;
import com.dora.jagent.util.PasswordUtils;
import com.dora.jagent.util.VerificationCodeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

// @Service 表示这是一个业务层组件。
// Spring 启动时会把它注册成 Bean，这样 AuthController 里依赖的 AuthService
// 就终于有了一个真正的实现类可以注入。
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;

    @Override
    public void sendRegisterCode(SendRegisterCodeRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("email cannot be blank");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BizException("email already registered");
        }

        String code = VerificationCodeUtils.generate6DigitCode();
        verificationCodeService.saveRegisterCode(email, code);
        emailService.sendEmail(
                email,
                "JAgent 注册验证码",
                "你的注册验证码是：" + code + "，5分钟内有效。"
        );
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new BizException("email already registered");
        }

        String cachedCode = verificationCodeService.getRegisterCode(email);
        if (!StringUtils.hasText(cachedCode)) {
            throw new BizException("verification code expired or not sent");
        }
        if (!cachedCode.equals(request.getVerificationCode().trim())) {
            throw new BizException("verification code incorrect");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash(PasswordUtils.hash(request.getPassword().trim()))
                .displayName(request.getDisplayName().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();

        User savedUser = userRepository.save(user);
        verificationCodeService.deleteRegisterCode(email);

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        validateLoginRequest(request);

        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BizException("user not found"));

        if (!PasswordUtils.matches(request.getPassword().trim(), user.getPasswordHash())) {
            throw new BizException("password incorrect");
        }

        return LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .tokenPair(buildTokenPair(user))
                .build();
    }

    @Override
    public TokenPairResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            throw new IllegalArgumentException("refreshToken cannot be blank");
        }
        String refreshToken = request.getRefreshToken().trim();
        if (!jwtUtils.isRefreshToken(refreshToken)) {
            throw new BizException("invalid refresh token");
        }

        User user = userRepository.findByEmail(jwtUtils.getEmail(refreshToken))
                .orElseThrow(() -> new BizException("user not found"));
        return buildTokenPair(user);
    }

    @Override
    public ProfileResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("user not found"));
        return ProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build();
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("register request cannot be null");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("email cannot be blank");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("password cannot be blank");
        }
        if (!StringUtils.hasText(request.getDisplayName())) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (!StringUtils.hasText(request.getVerificationCode())) {
            throw new IllegalArgumentException("verificationCode cannot be blank");
        }
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("login request cannot be null");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("email cannot be blank");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("password cannot be blank");
        }
    }

    private TokenPairResponse buildTokenPair(User user) {
        return TokenPairResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtUtils.generateRefreshToken(user.getId(), user.getEmail()))
                .accessTokenExpireSeconds(jwtUtils.getAccessTokenExpireSeconds())
                .refreshTokenExpireSeconds(jwtUtils.getRefreshTokenExpireSeconds())
                .build();
    }
}
