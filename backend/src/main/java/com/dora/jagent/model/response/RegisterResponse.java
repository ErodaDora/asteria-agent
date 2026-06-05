package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// response 类表示“后端返回给前端的数据结构”。
// 注册成功后，先只返回最核心的用户信息。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    private String userId;
    private String email;
    private String displayName;
}
