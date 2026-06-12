package cn.bugstack.ai.domain.agent.service.workflow.vision;

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

    @Override
    public void saveFrame(CameraFrameEntity frame) {
        frameCache.put(frame.getSessionId(), frame);
        log.debug("保存摄像头帧: sessionId={}, size={}bytes", frame.getSessionId(),
                frame.getImageData() != null ? frame.getImageData().length : 0);
    }

    @Override
    public CameraFrameEntity getFrame(String sessionId) {
        return frameCache.get(sessionId);
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
