package cn.bugstack.ai.domain.agent.service.workflow.vision;

import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FrameRequestService {

    private final Map<String, CameraFrameEntity> frameCache = new ConcurrentHashMap<>();
    private final Map<String, Object> requestLocks = new ConcurrentHashMap<>();

    public CameraFrameEntity waitForFrame(String sessionId, long timeoutMs) {
        Object lock = requestLocks.computeIfAbsent(sessionId, k -> new Object());
        synchronized (lock) {
            CameraFrameEntity frame = frameCache.get(sessionId);
            if (frame != null) {
                frameCache.remove(sessionId);
                return frame;
            }
            try {
                lock.wait(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            frame = frameCache.remove(sessionId);
            return frame;
        }
    }

    public void completeRequest(String sessionId, CameraFrameEntity frame) {
        Object lock = requestLocks.get(sessionId);
        if (lock != null) {
            synchronized (lock) {
                frameCache.put(sessionId, frame);
                lock.notifyAll();
            }
        }
    }
}
