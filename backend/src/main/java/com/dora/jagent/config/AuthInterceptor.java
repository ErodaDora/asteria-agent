package com.dora.jagent.config;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

// 认证拦截器：
// 只负责 access token 校验。
// 当前不做验证码，也不做 Redis 会话控制。
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException("missing access token");
        }

        String token = authorization.substring(7);
        if (!jwtUtils.isAccessToken(token)) {
            throw new BizException("invalid access token");
        }

        request.setAttribute("currentUserId", jwtUtils.getUserId(token));
        request.setAttribute("currentUserEmail", jwtUtils.getEmail(token));
        return true;
    }
}
