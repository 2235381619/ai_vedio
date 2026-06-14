package cn.bugstack.ai.domain.agent.service.vision;

import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;
import cn.bugstack.ai.domain.agent.service.ICameraFrameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CameraFrameServiceImpl implements ICameraFrameService {

    private final Map<String, CameraFrameEntity> frameCache = new ConcurrentHashMap<>();
    private final Map<String, Object> requestLocks = new ConcurrentHashMap<>();

    @Override
    public void saveFrame(CameraFrameEntity frame) {
        String sessionId = frame.getSessionId();
        frameCache.put(sessionId, frame);
        // 通知等待该 sessionId 帧的线程
        Object lock = requestLocks.get(sessionId);
        if (lock != null) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        log.debug("保存摄像头帧: sessionId={}, size={}bytes", sessionId,
                frame.getImageData() != null ? frame.getImageData().length : 0);
    }

    @Override
    public CameraFrameEntity getFrame(String sessionId) {
        return frameCache.get(sessionId);
    }

    @Override
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

    @Override
    public void clearFrame(String sessionId) {
        frameCache.remove(sessionId);
        log.debug("清除摄像头帧: sessionId={}", sessionId);
    }

    @Override
    public boolean hasFrame(String sessionId) {
        return frameCache.containsKey(sessionId);
    }
}
