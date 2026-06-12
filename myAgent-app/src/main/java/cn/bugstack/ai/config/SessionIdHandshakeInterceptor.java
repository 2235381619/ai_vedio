package cn.bugstack.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

public class SessionIdHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SessionIdHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null && query.contains("sessionId=")) {
            String sessionId = query.substring(query.indexOf("sessionId=") + "sessionId=".length());
            if (sessionId.contains("&")) {
                sessionId = sessionId.substring(0, sessionId.indexOf("&"));
            }
            attributes.put("sessionId", sessionId);
            log.debug("WebSocket 握手提取 sessionId: {}", sessionId);
            return true;
        }
        log.warn("WebSocket 握手缺少 sessionId 参数");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
