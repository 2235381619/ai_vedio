package cn.bugstack.ai.cases.websocket;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /**
     * 每个会话一把发送锁。Tomcat 的 WebSocketSession 不是线程安全的，
     * 多线程并发向同一连接发送（如 TTS 二进制音频与摄像头 ack 文本同时发）
     * 会触发 “invalid state [BINARY_PARTIAL_WRITING]”。所有发送都必须串行化。
     */
    private final ConcurrentHashMap<String, Object> sendLocks = new ConcurrentHashMap<>();

    private Object sendLock(String sessionId) {
        return sendLocks.computeIfAbsent(sessionId, k -> new Object());
    }

    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.debug("注册 WebSocket 会话: sessionId={}", sessionId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        sendLocks.remove(sessionId);
        log.debug("移除 WebSocket 会话: sessionId={}", sessionId);
    }

    public boolean isOpen(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public void sendMessage(String sessionId, Consumer<JSONObject> messageBuilder) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            JSONObject message = new JSONObject();
            messageBuilder.accept(message);
            TextMessage textMessage = new TextMessage(message.toJSONString());
            synchronized (sendLock(sessionId)) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.error("发送消息失败: sessionId={}", sessionId, e);
                }
            }
        }
    }

    /** 发送二进制消息（如 TTS 音频），与文本发送共用同一把锁，保证串行。 */
    public void sendBinary(String sessionId, byte[] data) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            synchronized (sendLock(sessionId)) {
                try {
                    session.sendMessage(new BinaryMessage(data));
                } catch (IOException e) {
                    log.error("发送二进制消息失败: sessionId={}", sessionId, e);
                }
            }
        }
    }

    /** 发送文本消息（原始字符串），与其它发送共用同一把锁。 */
    public void sendText(String sessionId, String text) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            synchronized (sendLock(sessionId)) {
                try {
                    session.sendMessage(new TextMessage(text));
                } catch (IOException e) {
                    log.error("发送文本消息失败: sessionId={}", sessionId, e);
                }
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
