package cn.bugstack.ai.domain.session.service;

import cn.bugstack.ai.domain.session.model.entity.SessionEntity;

public interface ISessionService {

    SessionEntity createSession(String userId, SessionEntity.SessionType type);

    SessionEntity getSession(String sessionId);

    void touchSession(String sessionId);

    void closeSession(String sessionId);

    void cleanupExpiredSessions();

    boolean isValidSession(String sessionId);

}
