package com.dora.jagent.service;

// 邮件发送能力：
// 这条线服务于验证码发送，不是 JWT 本身的一部分。
public interface EmailService {

    void sendEmail(String to, String subject, String content);
}
