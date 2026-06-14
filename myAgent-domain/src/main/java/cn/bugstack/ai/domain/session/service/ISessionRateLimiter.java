package cn.bugstack.ai.domain.session.service;

public interface ISessionRateLimiter {

    /**
     * 尝试获取请求许可
     * @param sessionId 会话ID
     * @param maxRequests 时间窗口内最大请求数
     * @return true=放行, false=限流
     */
    boolean tryAcquire(String sessionId, int maxRequests);

    /** 重置指定会话的计数 */
    void reset(String sessionId);
}
