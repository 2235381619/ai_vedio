package cn.bugstack.ai.domain.session.service.ratelimit;

import cn.bugstack.ai.domain.session.service.ISessionRateLimiter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SessionRateLimiter implements ISessionRateLimiter {

    private final Cache<String, AtomicInteger> counter;

    public SessionRateLimiter() {
        this.counter = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public boolean tryAcquire(String sessionId, int maxRequests) {
        AtomicInteger count = counter.getIfPresent(sessionId);
        if (count == null) {
            counter.put(sessionId, new AtomicInteger(1));
            return true;
        }
        int current = count.incrementAndGet();
        if (current > maxRequests) {
            log.warn("会话被限流: sessionId={}, 当前次数={}/{}", sessionId, current, maxRequests);
            return false;
        }
        return true;
    }

    @Override
    public void reset(String sessionId) {
        counter.invalidate(sessionId);
    }
}
