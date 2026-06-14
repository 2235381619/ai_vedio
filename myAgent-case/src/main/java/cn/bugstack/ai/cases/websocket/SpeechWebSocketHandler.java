package cn.bugstack.ai.cases.websocket;

import cn.bugstack.ai.cases.conversation.IConversationCase;
import cn.bugstack.ai.cases.model.ConversationResult;
import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;
import cn.bugstack.ai.domain.agent.service.IAsrService;
import cn.bugstack.ai.domain.agent.service.ICameraFrameService;
import cn.bugstack.ai.domain.session.service.ISessionService;
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

    private final IConversationCase conversationCase;
    private final ICameraFrameService cameraFrameService;
    private final IAsrService asrService;
    private final ISessionService sessionService;
    private final WebSocketSessionManager sessionManager;

    public SpeechWebSocketHandler(IConversationCase conversationCase,
                                  ICameraFrameService cameraFrameService,
                                  IAsrService asrService,
                                  ISessionService sessionService,
                                  WebSocketSessionManager sessionManager) {
        this.conversationCase = conversationCase;
        this.cameraFrameService = cameraFrameService;
        this.asrService = asrService;
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
                case "start_asr":
                    handleStartAsr(sessionId, session);
                    break;
                case "end_asr":
                    conversationCase.endAsr(sessionId);
                    session.getAttributes().remove("asrStarted");
                    break;
                case "cancel":
                    conversationCase.cancel(sessionId);
                    break;
                case "text":
                    handleText(sessionId, json.getString("message"),
                            (Long) session.getAttributes().get("conversationId"));
                    break;
                case "camera_frame":
                    handleCameraFrame(sessionId, json);
                    break;
                default:
                    log.warn("未知消息类型: {}", type);
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
            handleStartAsr(sessionId, session);
        }
        conversationCase.sendAudioChunk(sessionId, audioData);
    }

    private void handleStartAsr(String sessionId, WebSocketSession session) {
        log.info("开始流式语音识别: sessionId={}", sessionId);
        session.getAttributes().put("asrStarted", true);
        Long conversationId = (Long) session.getAttributes().get("conversationId");

        asrService.startStreaming(sessionId, response -> {
            try {
                String text = response.getText();
                log.info("ASR 识别到内容: sessionId={}, text={}", sessionId, text);
                if (!sessionManager.isOpen(sessionId)) return;

                sessionManager.sendMessage(sessionId, msg -> {
                    msg.put("type", "asr_result");
                    msg.put("sessionId", sessionId);
                    msg.put("text", text);
                    msg.put("confidence", response.getConfidence());
                    msg.put("isFinal", response.getIsFinal());
                });

                if (text != null && !text.isBlank()) {
                    handleText(sessionId, text, conversationId);
                }
            } catch (Exception e) {
                log.error("处理 ASR 结果失败: sessionId={}", sessionId, e);
            }
        });
    }

    private void handleText(String sessionId, String text, Long conversationId) throws IOException {
        ConversationResult result = conversationCase.processText(sessionId, text, conversationId);

        if (result == null) {
            log.warn("文本处理返回空，跳过: sessionId={}", sessionId);
            return;
        }
        if (result.isError()) {
            sessionManager.sendError(sessionId, "AI 暂时无法回复");
            return;
        }

        WebSocketSession wsSession = sessionManager.getSession(sessionId);
        if (wsSession != null && wsSession.isOpen()) {
            wsSession.sendMessage(new BinaryMessage(result.getAudioData()));
            sessionManager.sendMessage(sessionId, msg -> {
                msg.put("type", "tts_done");
                msg.put("sessionId", sessionId);
                msg.put("text", result.getText());
                msg.put("agentType", result.getAgentType() != null
                        ? result.getAgentType().toString() : "TEXT");
            });
        }
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

            sessionManager.sendMessage(sessionId, msg -> {
                msg.put("type", "camera_frame_ack");
                msg.put("sessionId", sessionId);
                msg.put("success", true);
                msg.put("timestamp", System.currentTimeMillis());
            });
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
        conversationCase.endSession(sessionId);
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
