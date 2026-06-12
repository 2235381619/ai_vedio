package cn.bugstack.ai.domain.agent.service.workflow.session;

import cn.bugstack.ai.domain.agent.model.entity.SessionEntity;

public interface ISessionService {

    SessionEntity createSession(String userId, SessionEntity.SessionType type);

    SessionEntity getSession(String sessionId);

    void touchSession(String sessionId);

    void closeSession(String sessionId);

    void cleanupExpiredSessions();

    boolean isValidSession(String sessionId);

}