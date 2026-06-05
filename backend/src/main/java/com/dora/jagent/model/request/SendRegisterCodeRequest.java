package com.dora.jagent.model.request;

import lombok.Data;

// 发送注册验证码接口的最小入参。
@Data
public class SendRegisterCodeRequest {

    private String email;
}
