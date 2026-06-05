package com.dora.jagent.model.request;

import lombok.Data;

// request 类表示“前端传给后端的入参长什么样”。
// 注册时，前端至少要给邮箱、密码、展示名。
@Data
public class RegisterRequest {

    private String email;
    private String password;
    private String displayName;
    private String verificationCode;
}
