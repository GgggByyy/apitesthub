package com.apitesthub.interceptor;

import com.apitesthub.config.JwtConfig;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 统一鉴权拦截器：校验 Authorization: Bearer <token>，
 * 通过后把 userId 放入 request attribute，Controller 用 @RequestAttribute 取用。
 * 注册/登录接口在 WebConfig 中排除。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtConfig jwtConfig;

    public JwtInterceptor(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return false;
        }
        try {
            Long userId = jwtConfig.getUserIdFromToken(auth.substring(7));
            request.setAttribute("userId", userId);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response);
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"message\":\"unauthorized: missing or invalid token\",\"data\":null}");
    }
}
