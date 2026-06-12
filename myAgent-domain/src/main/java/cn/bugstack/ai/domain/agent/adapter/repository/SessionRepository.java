package cn.bugstack.ai.domain.agent.adapter.repository;

import cn.bugstack.ai.domain.agent.model.entity.SessionEntity;

import java.util.List;

public interface SessionRepository {
    SessionEntity create(SessionEntity session);
    SessionEntity findById(String sessionId);
    SessionEntity update(SessionEntity session);
    void deleteById(String sessionId);
    List<SessionEntity> findByUserId(String userId);
    void cleanupExpired(long maxIdleMs);
}
