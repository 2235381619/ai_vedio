package cn.bugstack.ai.domain.agent.service.workflow.agent;

import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AgentType;
import cn.bugstack.ai.domain.agent.model.valobj.SessionContextHolder;
import cn.bugstack.ai.domain.agent.service.IAgentFlowService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AgentFlowServiceImpl implements IAgentFlowService {

    private final CompiledGraph conversationWorkflow;

    public AgentFlowServiceImpl(CompiledGraph conversationWorkflow) {
        this.conversationWorkflow = conversationWorkflow;
    }

    @Override
    public AgentResponseEntity process(AgentRequestEntity request) {
        String sessionId = request.getSessionId();
        String text = request.getText();
        log.info("AgentFlow 处理请求: sessionId={}, text={}", sessionId, text);

        SessionContextHolder.setSessionId(sessionId);
        try {
            Map<String, Object> input = Map.of(
                    "input", text,
                    "sessionId", sessionId,
                    "vision_output", ""
            );

            NodeOutput lastOutput = conversationWorkflow.stream(input)
                    .doOnNext(output -> {
                        if (output instanceof StreamingOutput<?> streaming) {
                            log.debug("工作流节点: node={}, data={}", streaming.node(), streaming.message());
                        }
                    })
                    .blockLast();

            if (lastOutput == null) {
                log.warn("工作流返回空结果: sessionId={}", sessionId);
                return buildResponse(sessionId, "抱歉，我没有理解您的问题。", AgentType.TEXT);
            }

            var state = lastOutput.state();

            var visionOutput = state.value("vision_output");
            if (visionOutput.isPresent()) {
                String response = visionOutput.get().toString();
                if (!response.isBlank()) {
                    log.info("视觉处理完成: sessionId={}, response={}", sessionId, truncate(response, 100));
                    return buildResponse(sessionId, response, AgentType.VISION);
                }
            }

            var textOutput = state.value("text_output");
            if (textOutput.isPresent()) {
                String response = extractText(textOutput.get());
                if (response != null && !response.isBlank()) {
                    log.info("文本处理完成: sessionId={}, response={}", sessionId, truncate(response, 100));
                    return buildResponse(sessionId, response, AgentType.TEXT);
                }
            }

            log.warn("工作流无法提取有效响应: sessionId={}", sessionId);
            return buildResponse(sessionId, "抱歉，我暂时无法回答这个问题。", AgentType.TEXT);

        } catch (Exception e) {
            log.error("AgentFlow 处理异常: sessionId={}", sessionId, e);
            return buildResponse(sessionId, "抱歉，处理您的请求时出现异常。", AgentType.TEXT);
        } finally {
            SessionContextHolder.clear();
        }
    }

    private String extractText(Object value) {
        if (value == null) return null;
        String text = value.toString();
        if (text.startsWith("AssistantMessage") || text.contains("messageType=")) {
            return null;
        }
        return text.isBlank() ? null : text;
    }

    private AgentResponseEntity buildResponse(String sessionId, String text, AgentType agentType) {
        return AgentResponseEntity.builder()
                .sessionId(sessionId).response(text).agentType(agentType).build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
