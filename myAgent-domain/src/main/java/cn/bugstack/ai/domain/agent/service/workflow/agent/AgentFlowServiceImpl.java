package cn.bugstack.ai.domain.agent.service.workflow.agent;

import cn.bugstack.ai.domain.agent.model.entity.AgentOutput;
import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AgentType;
import cn.bugstack.ai.domain.agent.model.valobj.SessionContextHolder;
import cn.bugstack.ai.domain.agent.service.IAgentFlowService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
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
                AgentOutput visionAgentOutput = extractAgentOutput(visionOutput.get());
                if (visionAgentOutput != null && visionAgentOutput.getResponse() != null && !visionAgentOutput.getResponse().isBlank()) {
                    log.info("视觉处理完成: sessionId={}, instruction={}", sessionId, visionAgentOutput.getInstruction());
                    return AgentResponseEntity.builder()
                            .sessionId(sessionId)
                            .response(visionAgentOutput.getResponse())
                            .instruction(visionAgentOutput.getInstruction())
                            .agentType(AgentType.VISION)
                            .build();
                }
            }

            // 从 text_output 读取结构化输出
            var textOutput = state.value("text_output");
            if (textOutput.isPresent()) {
                AgentOutput agentOutput = extractAgentOutput(textOutput.get());
                if (agentOutput != null && agentOutput.getResponse() != null && !agentOutput.getResponse().isBlank()) {
                    log.info("文本输出: sessionId={}, response={}, instruction={}",
                            sessionId, truncate(agentOutput.getResponse(), 100), agentOutput.getInstruction());
                    return AgentResponseEntity.builder()
                            .sessionId(sessionId)
                            .response(agentOutput.getResponse())
                            .instruction(agentOutput.getInstruction())
                            .agentType(AgentType.TEXT)
                            .build();
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

    private AgentOutput extractAgentOutput(Object value) {
        if (value == null) return null;
        if (value instanceof AgentOutput) {
            return (AgentOutput) value;
        }
        String json = value.toString().trim();
        // 去掉 AssistantMessage 包装，提取 textContent
        if (json.contains("textContent=")) {
            int start = json.indexOf("textContent=") + "textContent=".length();
            // 跳过可能的引号
            if (start < json.length() && json.charAt(start) == '{') {
                int end = json.indexOf('}', start);
                if (end > start) {
                    json = json.substring(start, end + 1);
                }
            }
        }
        // 去掉可能的 markdown 代码块标记
        if (json.startsWith("```")) {
            json = json.replaceAll("```[a-zA-Z]*", "").trim();
        }
        try {
            return JSON.parseObject(json, AgentOutput.class);
        } catch (Exception e) {
            log.warn("解析 AgentOutput 失败，尝试作为纯文本处理: {}", e.getMessage());
            AgentOutput output = new AgentOutput();
            output.setResponse(json);
            return output;
        }
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
