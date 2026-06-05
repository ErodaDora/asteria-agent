package com.dora.jagent.exception;

// 业务异常：
// 用来表达“请求不合法”或“业务条件不满足”，
// 比如邮箱已注册、密码错误这类情况。
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
