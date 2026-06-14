package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;

public interface ICameraFrameService {
    void saveFrame(CameraFrameEntity frame);
    CameraFrameEntity getFrame(String sessionId);
    CameraFrameEntity waitForFrame(String sessionId, long timeoutMs);
    void clearFrame(String sessionId);
    boolean hasFrame(String sessionId);
}
