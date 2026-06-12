package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;
import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsResponseEntity;
import cn.bugstack.ai.domain.agent.service.IAgentFlowService;
import cn.bugstack.ai.domain.agent.service.IAsrService;
import cn.bugstack.ai.domain.agent.service.ICameraFrameService;
import cn.bugstack.ai.domain.agent.service.ITtsService;
import cn.bugstack.ai.domain.agent.service.ISessionService;
import cn.bugstack.ai.domain.agent.service.workflow.vision.FrameRequestService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;

@Slf4j
@Component
public class SpeechWebSocketHandler extends AbstractWebSocketHandler {

    private final IAsrService asrService;
    private final ITtsService ttsService;
    private final ICameraFrameService cameraFrameService;
    private final FrameRequestService frameRequestService;
    private final IAgentFlowService agentFlowService;
    private final ISessionService sessionService;
    private final WebSocketSessionManager sessionManager;

    public SpeechWebSocketHandler(IAsrService asrService, ITtsService ttsService,
                                  ICameraFrameService cameraFrameService,
                                  FrameRequestService frameRequestService,
                                  IAgentFlowService agentFlowService,
                                  ISessionService sessionService,
                                  WebSocketSessionManager sessionManager) {
        this.asrService = asrService;
        this.ttsService = ttsService;
        this.cameraFrameService = cameraFrameService;
        this.frameRequestService = frameRequestService;
        this.agentFlowService = agentFlowService;
        this.sessionService = sessionService;
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            log.error("WebSocket 握手缺少 sessionId，拒绝连接");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (!sessionService.isValidSession(sessionId)) {
            log.error("无效的 sessionId: {}，拒绝连接", sessionId);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessionManager.registerSession(sessionId, session);
        sessionService.touchSession(sessionId);
        log.info("WebSocket 连接已建立: sessionId={}", sessionId);
        session.sendMessage(new TextMessage(JSON.toJSONString(new Message("connected", sessionId))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        String payload = message.getPayload();
        log.debug("收到文本消息: sessionId={}, payload={}", sessionId, payload);
        try {
            JSONObject json = JSON.parseObject(payload);
            String type = json.getString("type");
            switch (type) {
                case "start_asr": handleStartAsr(sessionId); break;
                case "end_asr": handleEndAsr(sessionId); break;
                case "cancel": handleCancel(sessionId); break;
                case "text": handleTextMessage(sessionId, json.getString("message")); break;
                case "camera_frame": handleCameraFrame(sessionId, json); break;
                default: log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理文本消息失败", e);
            sessionManager.sendError(sessionId, "消息格式错误");
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        ByteBuffer payload = message.getPayload();
        byte[] audioData = new byte[payload.remaining()];
        payload.get(audioData);
        log.debug("收到音频数据: sessionId={}, length={}", sessionId, audioData.length);
        if (session.getAttributes().get("asrStarted") == null) {
            log.info("音频数据到达时 ASR 未启动，自动启动: sessionId={}", sessionId);
            handleStartAsr(sessionId);
        }
        asrService.sendAudioChunk(sessionId, audioData);
    }

    private void handleStartAsr(String sessionId) {
        log.info("开始流式语音识别: sessionId={}", sessionId);
        WebSocketSession wsSession = sessionManager.getSession(sessionId);
        if (wsSession != null) wsSession.getAttributes().put("asrStarted", true);
        asrService.startStreaming(sessionId, response -> {
            try {
                String text = response.getText();
                log.info("ASR 识别到内容: sessionId={}, text={}", sessionId, text);
                if (!sessionManager.isOpen(sessionId)) return;

                JSONObject result = new JSONObject();
                result.put("type", "asr_result");
                result.put("sessionId", sessionId);
                result.put("text", text);
                result.put("confidence", response.getConfidence());
                result.put("isFinal", response.getIsFinal());
                sessionManager.sendMessage(sessionId, msg -> msg.putAll(result));

                if (text != null && !text.isBlank()) {
                    handleTextMessage(sessionId, text);
                }
            } catch (Exception e) {
                log.error("处理 ASR 结果失败: sessionId={}", sessionId, e);
            }
        });
    }

    private void handleEndAsr(String sessionId) {
        log.info("结束流式语音识别: sessionId={}", sessionId);
        asrService.endSession(sessionId);
    }

    private void handleCancel(String sessionId) {
        log.info("取消会话: sessionId={}", sessionId);
        asrService.cancelSession(sessionId);
        ttsService.cancelSession(sessionId);
        cameraFrameService.clearFrame(sessionId);
    }

    private void handleTextMessage(String sessionId, String text) throws IOException {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过处理: sessionId={}", sessionId);
            return;
        }
        log.info("ASR 识别文本: sessionId={}, text={}", sessionId, text);

        AgentRequestEntity agentRequest = AgentRequestEntity.builder()
                .sessionId(sessionId).text(text).build();
        AgentResponseEntity agentResponse = agentFlowService.process(agentRequest);
        String agentText = agentResponse.getResponse();

        if (agentText == null || agentText.isBlank()) {
            log.warn("Agent 返回空响应，跳过 TTS: sessionId={}", sessionId);
            sessionManager.sendError(sessionId, "AI 暂时无法回复");
            return;
        }

        log.info("Agent 响应: sessionId={}, agentType={}, response={}",
                sessionId, agentResponse.getAgentType(), truncate(agentText, 100));

        TtsRequestEntity ttsRequest = TtsRequestEntity.builder()
                .sessionId(sessionId).text(agentText)
                .voice("Cherry").languageType("Chinese").mode("server_commit")
                .speechRate(1.0f).volume(50).pitchRate(1.0f).build();

        try {
            TtsResponseEntity ttsResponse = ttsService.synthesize(ttsRequest);
            WebSocketSession wsSession = sessionManager.getSession(sessionId);
            if (wsSession != null && wsSession.isOpen()) {
                wsSession.sendMessage(new BinaryMessage(ttsResponse.getAudioData()));
                JSONObject result = new JSONObject();
                result.put("type", "tts_done");
                result.put("sessionId", sessionId);
                result.put("text", agentText);
                result.put("agentType", agentResponse.getAgentType().getCode());
                wsSession.sendMessage(new TextMessage(result.toJSONString()));
            }
        } catch (Exception e) {
            log.error("语音合成失败: sessionId={}", sessionId, e);
            sessionManager.sendError(sessionId, "语音合成失败: " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private void handleCameraFrame(String sessionId, JSONObject json) {
        String imageData = json.getString("imageData");
        if (imageData == null || imageData.isEmpty()) {
            log.warn("摄像头帧数据为空: sessionId={}", sessionId);
            sessionManager.sendError(sessionId, "摄像头帧数据为空");
            return;
        }
        try {
            String base64Data = imageData.contains(",") ? imageData.split(",")[1] : imageData;
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            CameraFrameEntity frame = CameraFrameEntity.builder()
                    .sessionId(sessionId).imageData(imageBytes)
                    .format(json.getString("format"))
                    .width(json.getInteger("width"))
                    .height(json.getInteger("height"))
                    .timestamp(json.getLong("timestamp")).build();
            cameraFrameService.saveFrame(frame);
            frameRequestService.completeRequest(sessionId, frame);
            JSONObject response = new JSONObject();
            response.put("type", "camera_frame_ack");
            response.put("sessionId", sessionId);
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            WebSocketSession wsSession = sessionManager.getSession(sessionId);
            if (wsSession != null && wsSession.isOpen()) {
                wsSession.sendMessage(new TextMessage(response.toJSONString()));
            }
            log.debug("摄像头帧已保存: sessionId={}, size={}bytes", sessionId, imageBytes.length);
        } catch (Exception e) {
            log.error("处理摄像头帧失败: sessionId={}", sessionId, e);
            sessionManager.sendError(sessionId, "处理摄像头帧失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = (String) session.getAttributes().get("sessionId");
        sessionManager.removeSession(sessionId);
        cameraFrameService.clearFrame(sessionId);
        asrService.endSession(sessionId);
        ttsService.endSession(sessionId);
        log.info("WebSocket 连接已关闭: sessionId={}, status={}", sessionId, status);
    }

    private static class Message {
        private String type;
        private String sessionId;
        public Message(String type, String sessionId) {
            this.type = type;
            this.sessionId = sessionId;
        }
        public String getType() { return type; }
        public String getSessionId() { return sessionId; }
    }
}
