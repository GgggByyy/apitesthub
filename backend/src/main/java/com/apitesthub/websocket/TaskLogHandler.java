package com.apitesthub.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 任务日志 WS 端点：ws://host/ws/task?taskId=1
 * 连接建立即完成订阅（taskId 从 query 取，鉴权已在握手拦截器完成），
 * 之后服务端单向推送执行事件，客户端无需再发消息。
 */
@Component
public class TaskLogHandler extends TextWebSocketHandler {

    private final TaskEventBroadcaster broadcaster;

    public TaskLogHandler(TaskEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        URI uri = session.getUri();
        String taskIdStr = uri == null ? null
                : UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("taskId");
        if (taskIdStr == null) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }
        session.getAttributes().put("taskId", taskIdStr);
        // WebSocketSession 并发 sendMessage 非线程安全：Decorator 串行化发送 +
        // 缓冲上限（512KB / 5s 发送超时），慢消费者自动断开而不拖垮执行线程
        broadcaster.subscribe(Long.parseLong(taskIdStr),
                new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unsubscribe(session);
    }
}
