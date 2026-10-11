package com.apitesthub.config;

import com.apitesthub.config.JwtConfig;
import com.apitesthub.websocket.TaskLogHandler;
import io.jsonwebtoken.JwtException;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 配置（M4）。
 *
 * 鉴权现实约束：浏览器 WebSocket API 不允许自定义 Authorization 头，
 * 因此 JWT 放在 query 参数（?token=xxx），握手拦截器校验、非法直接拒绝握手。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskLogHandler taskLogHandler;
    private final JwtConfig jwtConfig;

    public WebSocketConfig(TaskLogHandler taskLogHandler, JwtConfig jwtConfig) {
        this.taskLogHandler = taskLogHandler;
        this.jwtConfig = jwtConfig;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(taskLogHandler, "/ws/task")
                .addInterceptors(jwtHandshakeInterceptor())
                .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173");
    }

    /** 握手期校验 query 里的 token（浏览器 WS API 无法自定义 Authorization 头） */
    private HandshakeInterceptor jwtHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                           @NonNull ServerHttpResponse response,
                                           @NonNull WebSocketHandler wsHandler,
                                           @NonNull Map<String, Object> attributes) {
                if (request instanceof ServletServerHttpRequest servletRequest) {
                    String token = servletRequest.getServletRequest().getParameter("token");
                    if (token != null) {
                        try {
                            attributes.put("userId", jwtConfig.getUserIdFromToken(token));
                            return true;
                        } catch (JwtException | IllegalArgumentException e) {
                            return false; // 无效 token 拒绝握手
                        }
                    }
                }
                return false;
            }

            @Override
            public void afterHandshake(@NonNull ServerHttpRequest request,
                                       @NonNull ServerHttpResponse response,
                                       @NonNull WebSocketHandler wsHandler, Exception exception) {
            }
        };
    }
}
