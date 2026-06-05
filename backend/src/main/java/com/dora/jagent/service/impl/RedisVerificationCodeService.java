package com.dora.jagent.service.impl;

import com.dora.jagent.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisVerificationCodeService implements VerificationCodeService {

    private static final String REGISTER_CODE_PREFIX = "jagent:register:code:";

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${verification.register-code-expire-seconds}")
    private Long registerCodeExpireSeconds;

    @Override
    public void saveRegisterCode(String email, String code) {
        stringRedisTemplate.opsForValue().set(
                buildRegisterCodeKey(email),
                code,
                Duration.ofSeconds(registerCodeExpireSeconds)
        );
    }

    @Override
    public String getRegisterCode(String email) {
        return stringRedisTemplate.opsForValue().get(buildRegisterCodeKey(email));
    }

    @Override
    public void deleteRegisterCode(String email) {
        stringRedisTemplate.delete(buildRegisterCodeKey(email));
    }

    private String buildRegisterCodeKey(String email) {
        return REGISTER_CODE_PREFIX + email.toLowerCase().trim();
    }
}
