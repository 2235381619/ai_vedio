package cn.bugstack.ai.domain.agent.service.workflow.vision;

import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;

public interface IFrameCaptureService {
    CameraFrameEntity captureFrame(String sessionId);
}
