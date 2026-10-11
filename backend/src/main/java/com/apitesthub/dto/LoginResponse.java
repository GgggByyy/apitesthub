package com.apitesthub.dto;

/**
 * 登录成功返回的 JWT。
 */
public class LoginResponse {

    private final String token;

    public LoginResponse(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
