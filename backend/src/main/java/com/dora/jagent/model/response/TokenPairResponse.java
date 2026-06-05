package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 双 token 返回结构：
// access token 给业务接口用，refresh token 给续签用。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPairResponse {

    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpireSeconds;
    private Long refreshTokenExpireSeconds;
}
