package com.apitesthub.service;

import com.apitesthub.config.JwtConfig;
import com.apitesthub.dto.LoginRequest;
import com.apitesthub.dto.RegisterRequest;
import com.apitesthub.dto.UserVO;
import com.apitesthub.entity.User;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户注册 / 登录 / 信息查询。
 * 安全要点：
 *  1. 密码只存 BCrypt 慢哈希（每次加密结果都不同，自带随机盐，matches 比对）
 *  2. 登录失败统一提示"用户名或密码错误"，不区分哪个错（防账号枚举）
 *  3. /me 返回 UserVO，绝不回传密码字段
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtConfig jwtConfig;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, JwtConfig jwtConfig) {
        this.userMapper = userMapper;
        this.jwtConfig = jwtConfig;
    }

    public void register(RegisterRequest req) {
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exist != null) {
            throw new BizException(400, "username already exists: " + req.getUsername());
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("USER");
        userMapper.insert(user);
    }

    public String login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(401, "incorrect username or password");
        }
        return jwtConfig.generateToken(user.getId());
    }

    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "user not found");
        }
        return new UserVO(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
