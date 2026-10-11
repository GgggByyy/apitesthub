package com.apitesthub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务事件广播器（M4 核心）。
 *
 * 订阅关系：ConcurrentHashMap<taskId, Set<WebSocketSession>>——
 * 同一任务可被多个浏览器窗口同时观看（Set 去重），一个会话只订阅一个任务。
 *
 * 并发要点：WebSocketSession 本身不保证并发 sendMessage 线程安全，
 * 注册时用 ConcurrentWebSocketSessionDecorator 包装（发送串行化 + 缓冲上限，
 * 慢消费者不会拖垮执行线程——超缓冲直接关连接）。
 *
 * Redis/执行线程与 WS 推送解耦：广播失败只记日志，绝不影响用例执行。
 */
@Component
public class TaskEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TaskEventBroadcaster.class);

    private final Map<Long, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TaskEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void subscribe(Long taskId, WebSocketSession session) {
        subscriptions.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("ws subscribed task {} (sessions={})", taskId, subscriptions.get(taskId).size());
    }

    public void unsubscribe(WebSocketSession session) {
        // Decorator 的 getId() 委托原始 session，按 id 移除即可
        subscriptions.values().forEach(set -> set.removeIf(s -> s.getId().equals(session.getId())));
    }

    /** 向订阅该任务的全部会话推送事件 JSON；单会话失败不影响其他会话 */
    public void broadcast(Long taskId, Object event) {
        Set<WebSocketSession> sessions = subscriptions.get(taskId);
        if (sessions == null || sessions.isEmpty()) {
            return; // 无人观看（前端没开详情页）——正常情况，静默跳过
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.warn("serialize event failed: {}", e.getMessage());
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.warn("ws send failed, removing session: {}", e.getMessage());
                sessions.remove(session);
            }
        }
    }
}
