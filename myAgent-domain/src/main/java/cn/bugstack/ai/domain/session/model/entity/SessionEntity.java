package cn.bugstack.ai.domain.session.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity {
    private String sessionId;
    private String userId;
    private SessionType type;
    private SessionStatus status;
    private long createdAt;
    private long lastActiveAt;
    private Map<String, Object> metadata;

    public enum SessionType {
        VOICE, TEXT, VIDEO
    }

    public enum SessionStatus {
        CREATED, ACTIVE, CLOSED
    }

    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
        if (this.status == SessionStatus.CREATED) {
            this.status = SessionStatus.ACTIVE;
        }
    }

    public void close() {
        this.status = SessionStatus.CLOSED;
    }

    public boolean isExpired(long maxIdleMs) {
        return System.currentTimeMillis() - lastActiveAt > maxIdleMs;
    }

    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    public void putMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new ConcurrentHashMap<>();
        }
        metadata.put(key, value);
    }
}
