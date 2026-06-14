package cn.bugstack.ai.cases.conversation;

import cn.bugstack.ai.cases.model.ConversationResult;
import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.TtsResponseEntity;
import cn.bugstack.ai.domain.agent.service.IAgentFlowService;
import cn.bugstack.ai.domain.agent.service.IAsrService;
import cn.bugstack.ai.domain.agent.service.ITtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ConversationCase implements IConversationCase {

    private final IAsrService asrService;
    private final IAgentFlowService agentFlowService;
    private final ITtsService ttsService;

    private final Map<String, Long> lastInputTime = new ConcurrentHashMap<>();
    private final Map<String, String> lastInputText = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 3000;

    public ConversationCase(IAsrService asrService,
                            IAgentFlowService agentFlowService,
                            ITtsService ttsService) {
        this.asrService = asrService;
        this.agentFlowService = agentFlowService;
        this.ttsService = ttsService;
    }

    @Override
    public void sendAudioChunk(String sessionId, byte[] audioData) {
        asrService.sendAudioChunk(sessionId, audioData);
    }

    @Override
    public void endAsr(String sessionId) {
        log.info("结束 ASR 会话: sessionId={}", sessionId);
        asrService.endSession(sessionId);
    }

    @Override
    public void cancel(String sessionId) {
        log.info("取消会话: sessionId={}", sessionId);
        asrService.cancelSession(sessionId);
        ttsService.cancelSession(sessionId);
        lastInputTime.remove(sessionId);
        lastInputText.remove(sessionId);
    }

    @Override
    public void endSession(String sessionId) {
        log.info("结束全部会话: sessionId={}", sessionId);
        asrService.endSession(sessionId);
        ttsService.endSession(sessionId);
        lastInputTime.remove(sessionId);
        lastInputText.remove(sessionId);
    }

    @Override
    public ConversationResult processText(String sessionId, String text, Long conversationId) {
        if (text == null || text.isBlank()) {
            log.warn("文本为空，跳过处理: sessionId={}", sessionId);
            return null;
        }

        long now = System.currentTimeMillis();
        String prevText = lastInputText.get(sessionId);
        Long prevTime = lastInputTime.get(sessionId);
        if (text.equals(prevText) && prevTime != null && (now - prevTime) < DEBOUNCE_MS) {
            log.debug("防抖跳过重复文本: sessionId={}, text={}", sessionId, text);
            return null;
        }
        lastInputText.put(sessionId, text);
        lastInputTime.put(sessionId, now);

        log.info("处理文本: sessionId={}, text={}", sessionId, text);

        AgentRequestEntity agentRequest = AgentRequestEntity.builder()
                .sessionId(sessionId).text(text)
                .conversationId(conversationId)
                .build();
        AgentResponseEntity agentResponse = agentFlowService.process(agentRequest);

        if (agentResponse == null || agentResponse.getResponse() == null || agentResponse.getResponse().isBlank()) {
            log.warn("Agent 返回空响应: sessionId={}", sessionId);
            return null;
        }

        log.info("Agent 响应: sessionId={}, agentType={}, instruction={}",
                sessionId, agentResponse.getAgentType(), agentResponse.getInstruction());

        TtsRequestEntity ttsRequest = TtsRequestEntity.builder()
                .sessionId(sessionId)
                .text(agentResponse.getResponse())
                .voice("Cherry").languageType("Chinese").mode("server_commit")
                .speechRate(1.0f).volume(50).pitchRate(1.0f)
                .instruction(agentResponse.getInstruction())
                .build();

        try {
            TtsResponseEntity ttsResponse = ttsService.synthesize(ttsRequest);
            return new ConversationResult(
                    ttsResponse.getAudioData(),
                    agentResponse.getResponse(),
                    agentResponse.getAgentType(),
                    agentResponse.getInstruction()
            );
        } catch (Exception e) {
            log.error("TTS 合成失败: sessionId={}", sessionId, e);
            return ConversationResult.error(agentResponse.getResponse(),
                    agentResponse.getAgentType(), agentResponse.getInstruction());
        }
    }
}
