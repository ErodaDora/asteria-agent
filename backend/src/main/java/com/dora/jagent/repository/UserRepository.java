package com.dora.jagent.repository;

import com.dora.jagent.model.entity.User;

import java.util.Optional;

// Repository 负责和数据库直接打交道。
// 当前阶段只保留认证场景真正需要的两个最小能力：
// 1. 按邮箱查询用户
// 2. 保存用户
public interface UserRepository {

    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    User save(User user);
}
