package cn.bugstack.ai.domain.session.adapter.repository;

import cn.bugstack.ai.domain.session.model.entity.SessionEntity;

import java.util.List;

public interface SessionRepository {
    SessionEntity create(SessionEntity session);
    SessionEntity findById(String sessionId);
    SessionEntity update(SessionEntity session);
    void delete(String sessionId);
    List<SessionEntity> findByUserId(String userId);
    List<SessionEntity> findByStatus(SessionEntity.SessionStatus status);
    boolean exists(String sessionId);
    void cleanupExpired(long maxIdleMs);
}
