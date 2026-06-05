package com.dora.jagent.service;

import com.dora.jagent.model.request.LoginRequest;
import com.dora.jagent.model.request.RefreshTokenRequest;
import com.dora.jagent.model.request.RegisterRequest;
import com.dora.jagent.model.request.SendRegisterCodeRequest;
import com.dora.jagent.model.response.LoginResponse;
import com.dora.jagent.model.response.ProfileResponse;
import com.dora.jagent.model.response.RegisterResponse;
import com.dora.jagent.model.response.TokenPairResponse;

// Service 接口负责定义“认证业务需要提供哪些能力”。
// 先写接口，再写实现类，是为了把“做什么”和“怎么做”分开。
public interface AuthService {

    void sendRegisterCode(SendRegisterCodeRequest request);

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    TokenPairResponse refreshToken(RefreshTokenRequest request);

    ProfileResponse getProfile(String userId);
}
