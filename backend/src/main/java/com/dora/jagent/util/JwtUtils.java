package com.dora.jagent.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

// JWT 工具类：
// 这里负责 token 的三类核心动作：
// 1. 生成 access token
// 2. 生成 refresh token
// 3. 解析并校验 token
//
// 注意：
// - 这是 JWT 相关逻辑
// - 不是邮箱验证码逻辑
// - 也不是 Redis 验证码存储逻辑
@Component
public class JwtUtils {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire-seconds}")
    private Long accessTokenExpireSeconds;

    @Value("${jwt.refresh-token-expire-seconds}")
    private Long refreshTokenExpireSeconds;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, String email) {
        return buildToken(userId, email, ACCESS_TOKEN_TYPE, accessTokenExpireSeconds);
    }

    public String generateRefreshToken(String userId, String email) {
        return buildToken(userId, email, REFRESH_TOKEN_TYPE, refreshTokenExpireSeconds);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(parseToken(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(parseToken(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public String getUserId(String token) {
        return parseToken(token).get(CLAIM_USER_ID, String.class);
    }

    public String getEmail(String token) {
        return parseToken(token).get(CLAIM_EMAIL, String.class);
    }

    public Long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }

    public Long getRefreshTokenExpireSeconds() {
        return refreshTokenExpireSeconds;
    }

    private String buildToken(String userId, String email, String tokenType, Long expireSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }
}
