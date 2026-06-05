package com.dora.jagent.model.request;

import lombok.Data;

// 登录接口的最小入参。
// 这一版只用 email + password 做登录。
@Data
public class LoginRequest {

    private String email;
    private String password;
}
