package com.apitesthub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "username must not be blank")
    @Size(min = 3, max = 50, message = "username length must be 3-50")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(min = 6, max = 64, message = "password length must be 6-64")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
