package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 登录成功后的最小返回结构。
// 现在 token 先用 mock 值占位，后面再替换成真正的 JWT。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String userId;
    private String email;
    private String displayName;
    private TokenPairResponse tokenPair;
}
