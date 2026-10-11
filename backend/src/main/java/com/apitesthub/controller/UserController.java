package com.apitesthub.controller;

import com.apitesthub.common.ApiResponse;
import com.apitesthub.dto.LoginRequest;
import com.apitesthub.dto.LoginResponse;
import com.apitesthub.dto.RegisterRequest;
import com.apitesthub.dto.UserVO;
import com.apitesthub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块：注册 / 登录 / 当前用户信息。
 * /me 由 JwtInterceptor 保护，userId 从拦截器放入的 request attribute 取得。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(new LoginResponse(userService.login(req)));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(userService.me(userId));
    }
}
