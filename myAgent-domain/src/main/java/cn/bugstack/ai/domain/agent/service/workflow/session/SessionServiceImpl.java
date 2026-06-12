package cn.bugstack.ai.domain.agent.service.workflow.session;

import cn.bugstack.ai.domain.agent.adapter.repository.SessionRepository;
import cn.bugstack.ai.domain.agent.model.entity.SessionEntity;
import cn.bugstack.ai.domain.agent.service.ISessionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SessionServiceImpl implements ISessionService {

    private final SessionRepository sessionRepository;

    private final Map<String, SessionEntity> sessionCache = new ConcurrentHashMap<>();

    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    public SessionServiceImpl(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @PostConstruct
    public void init() {
        log.info("Session 服务初始化完成");
    }

    @Override
    public SessionEntity createSession(String userId, SessionEntity.SessionType type) {
        String sessionId = generateSessionId();

        SessionEntity session = SessionEntity.builder()
                .sessionId(sessionId)
                .userId(userId)
                .type(type)
                .status(SessionEntity.SessionStatus.CREATED)
                .createdAt(System.currentTimeMillis())
                .lastActiveAt(System.currentTimeMillis())
                .metadata(new ConcurrentHashMap<>())
                .build();

        session = sessionRepository.create(session);
        sessionCache.put(sessionId, session);

        log.info("创建会话: sessionId={}, userId={}, type={}", sessionId, userId, type);

        return session;
    }

    @Override
    public SessionEntity getSession(String sessionId) {
        SessionEntity session = sessionCache.get(sessionId);
        if (session == null) {
            session = sessionRepository.findById(sessionId);
            if (session != null) {
                sessionCache.put(sessionId, session);
            }
        }

        if (session != null && session.getStatus() == SessionEntity.SessionStatus.CLOSED) {
            throw new IllegalStateException("会话已关闭: " + sessionId);
        }

        return session;
    }

    @Override
    public void touchSession(String sessionId) {
        Object lock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            SessionEntity session = getSession(sessionId);
            if (session != null) {
                session.touch();
                sessionRepository.update(session);
                sessionCache.put(sessionId, session);
                log.debug("更新会话活跃时间: sessionId={}", sessionId);
            }
        }
    }

    @Override
    public void closeSession(String sessionId) {
        Object lock = sessionLocks.remove(sessionId);
        if (lock != null) {
            synchronized (lock) {
                SessionEntity session = getSession(sessionId);
                if (session != null) {
                    session.close();
                    sessionRepository.update(session);
                    sessionCache.remove(sessionId);
                    log.info("关闭会话: sessionId={}", sessionId);
                }
            }
        }
    }

    @Override
    public void cleanupExpiredSessions() {
        long maxIdleMs = 30 * 60 * 1000;
        sessionRepository.cleanupExpired(maxIdleMs);

        sessionCache.entrySet().removeIf(entry -> {
            SessionEntity session = entry.getValue();
            boolean expired = session.isExpired(maxIdleMs);
            if (expired) {
                log.info("清理过期会话: sessionId={}", session.getSessionId());
            }
            return expired;
        });
    }

    @Override
    public boolean isValidSession(String sessionId) {
        SessionEntity session = getSession(sessionId);
        return session != null && session.getStatus() != SessionEntity.SessionStatus.CLOSED;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void scheduledCleanup() {
        cleanupExpiredSessions();
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

}