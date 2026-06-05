package com.dora.jagent.model.request;

import lombok.Data;

// refresh token 接口的最小入参。
// 用途：拿旧 refresh token 换一组新的 token。
@Data
public class RefreshTokenRequest {

    private String refreshToken;
}
