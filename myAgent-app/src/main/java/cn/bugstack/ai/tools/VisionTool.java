package cn.bugstack.ai.tools;

import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;
import cn.bugstack.ai.domain.agent.service.ICameraFrameService;
import cn.bugstack.ai.domain.agent.service.workflow.vision.FrameRequestService;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VisionTool {

    private final ICameraFrameService cameraFrameService;
    private final FrameRequestService frameRequestService;

    public VisionTool(ICameraFrameService cameraFrameService,
                      FrameRequestService frameRequestService) {
        this.cameraFrameService = cameraFrameService;
        this.frameRequestService = frameRequestService;
    }

    public CameraFrameEntity captureFrame(String sessionId) {
        log.info("截帧请求: sessionId={}", sessionId);

        CameraFrameEntity existing = cameraFrameService.getFrame(sessionId);
        if (existing != null) {
            cameraFrameService.clearFrame(sessionId);
            return existing;
        }

        CameraFrameEntity frame = frameRequestService.waitForFrame(sessionId, 5000);
        if (frame != null) {
            return frame;
        }

        log.warn("等待摄像头帧超时: sessionId={}", sessionId);
        return null;
    }
}
