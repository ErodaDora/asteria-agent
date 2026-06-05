package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.LoginRequest;
import com.dora.jagent.model.request.RefreshTokenRequest;
import com.dora.jagent.model.request.RegisterRequest;
import com.dora.jagent.model.request.SendRegisterCodeRequest;
import com.dora.jagent.model.response.LoginResponse;
import com.dora.jagent.model.response.ProfileResponse;
import com.dora.jagent.model.response.RegisterResponse;
import com.dora.jagent.model.response.TokenPairResponse;
import com.dora.jagent.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

// @RestController 表示这是一个 Web 接口类。
// Spring 会把这里的方法当成 HTTP 接口暴露出去。
@RestController
// @RequestMapping("/api/auth") 表示给这个类里的所有接口统一加一个前缀。
// 所以 register 的完整路径其实是 /api/auth/register。
@RequestMapping("/api/auth")
// @RequiredArgsConstructor 会为 final 字段自动生成构造器，
// Spring 就可以通过构造器注入 AuthService。
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 注册验证码发送接口：
    // 这是验证码线，不是 JWT 线。
    @PostMapping("/register/code")
    public ApiResponse<Void> sendRegisterCode(@RequestBody SendRegisterCodeRequest request) {
        authService.sendRegisterCode(request);
        return ApiResponse.success("register code sent", null);
    }

    // @PostMapping 表示这是一个 POST 接口。
    // @RequestBody 表示把请求体 JSON 自动映射成 RegisterRequest 对象。
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success("register success", authService.register(request));
    }

    // 输入：
    // @RequestBody RegisterRequest request
    // = 前端传来的 JSON 被转成 RegisterRequest 对象

    // 调用：
    // authService.register(request)
    // = 把注册请求交给 AuthService 处理

    // 输出：
    // ApiResponse.success(...)
    // = 把处理结果包装成统一响应返回给前端

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success("login success", authService.login(request));
    }

    // refresh token 专门用于换发新 token，不等于重新登录。
    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success("refresh success", authService.refreshToken(request));
    }

    // 这是一个受保护接口。
    // 要求前端带 Authorization: Bearer <access_token>
    @GetMapping("/me")
    public ApiResponse<ProfileResponse> me(HttpServletRequest request) {
        String currentUserId = (String) request.getAttribute("currentUserId");
        return ApiResponse.success(authService.getProfile(currentUserId));
    }

    // 一个最小健康检查接口，用来快速确认后端服务是否启动。
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
