package com.dora.jagent.util;

import java.security.SecureRandom;

// 验证码工具：
// 当前只负责生成 6 位数字验证码。
public final class VerificationCodeUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodeUtils() {
    }

    public static String generate6DigitCode() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
