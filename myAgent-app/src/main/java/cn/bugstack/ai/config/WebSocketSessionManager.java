package cn.bugstack.ai.config;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.debug("注册 WebSocket 会话: sessionId={}", sessionId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        log.debug("移除 WebSocket 会话: sessionId={}", sessionId);
    }

    public boolean isOpen(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public void sendMessage(String sessionId, Consumer<JSONObject> messageBuilder) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                JSONObject message = new JSONObject();
                messageBuilder.accept(message);
                session.sendMessage(new TextMessage(message.toJSONString()));
            } catch (IOException e) {
                log.error("发送消息失败: sessionId={}", sessionId, e);
            }
        }
    }

    public void sendError(String sessionId, String errorMessage) {
        sendMessage(sessionId, msg -> {
            msg.put("type", "error");
            msg.put("message", errorMessage);
        });
    }
}
