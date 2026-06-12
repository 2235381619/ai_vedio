package cn.bugstack.ai.domain.agent.model.valobj;

public class SessionContextHolder {
    private static final ThreadLocal<String> sessionIdHolder = new ThreadLocal<>();

    public static void setSessionId(String sessionId) {
        sessionIdHolder.set(sessionId);
    }

    public static String getSessionId() {
        return sessionIdHolder.get();
    }

    public static void clear() {
        sessionIdHolder.remove();
    }
}
