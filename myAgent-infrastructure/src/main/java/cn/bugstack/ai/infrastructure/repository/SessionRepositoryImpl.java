package cn.bugstack.ai.infrastructure.repository;

import cn.bugstack.ai.domain.agent.model.entity.SessionEntity;
import cn.bugstack.ai.domain.agent.repository.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class SessionRepositoryImpl implements SessionRepository {

    private final Map<String, SessionEntity> sessionStorage = new ConcurrentHashMap<>();

    @Override
    public SessionEntity create(SessionEntity session) {
        sessionStorage.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public SessionEntity findById(String sessionId) {
        return sessionStorage.get(sessionId);
    }

    @Override
    public List<SessionEntity> findByUserId(String userId) {
        return sessionStorage.values().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SessionEntity> findByStatus(SessionEntity.SessionStatus status) {
        return sessionStorage.values().stream()
                .filter(session -> status == session.getStatus())
                .collect(Collectors.toList());
    }

    @Override
    public SessionEntity update(SessionEntity session) {
        sessionStorage.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public void delete(String sessionId) {
        sessionStorage.remove(sessionId);
    }

    @Override
    public boolean exists(String sessionId) {
        return sessionStorage.containsKey(sessionId);
    }

    @Override
    public void cleanupExpired(long maxIdleMs) {
        sessionStorage.entrySet().removeIf(entry -> {
            SessionEntity session = entry.getValue();
            return session.isExpired(maxIdleMs);
        });
    }

}