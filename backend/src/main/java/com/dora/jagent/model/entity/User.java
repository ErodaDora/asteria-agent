package com.dora.jagent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// User 是后端内部的“用户实体表达”。
// 当前阶段先把它看成“系统里一个用户长什么样”，
// 后面接数据库时，它会更自然地映射到 user 表。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    // 注意这里不是 password，而是 passwordHash。
    // 这在提醒我们：密码不能明文存储，后面要做加密/哈希处理。
    private String passwordHash;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
