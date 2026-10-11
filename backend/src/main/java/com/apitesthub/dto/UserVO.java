package com.apitesthub.dto;

import java.time.LocalDateTime;

/**
 * 用户信息视图（不含密码哈希）。
 */
public class UserVO {

    private final Long id;
    private final String username;
    private final String role;
    private final LocalDateTime createdAt;

    public UserVO(Long id, String username, String role, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
