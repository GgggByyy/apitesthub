package com.apitesthub.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 * 无状态鉴权：服务端不存会话，签名保证 Payload 不可篡改；
 * secret 从配置注入（生产用环境变量 JWT_SECRET 覆盖），HMAC-SHA 密钥长度需 >= 256 bit。
 */
@Component
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    private static final long EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7 days in milliseconds

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 校验并解析 token，返回 userId。
     * 签名不对 / 过期 / 格式错误均抛 JwtException 子类，由拦截器统一转 401。
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
