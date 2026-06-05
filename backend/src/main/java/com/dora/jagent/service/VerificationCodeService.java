package com.dora.jagent.service;

// 验证码服务：
// 当前只实现“注册验证码”场景。
// 它依赖 Redis 保存短时验证码，不负责 token 签发。
public interface VerificationCodeService {

    void saveRegisterCode(String email, String code);

    String getRegisterCode(String email);

    void deleteRegisterCode(String email);
}
