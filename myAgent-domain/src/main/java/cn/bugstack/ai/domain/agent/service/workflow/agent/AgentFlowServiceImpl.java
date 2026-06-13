package cn.bugstack.ai.domain.agent.service.workflow.agent;

import cn.bugstack.ai.domain.agent.adapter.repository.IChatRepository;
import cn.bugstack.ai.domain.agent.model.entity.AgentOutput;
import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatConversationEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatMessageEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AgentType;
import cn.bugstack.ai.domain.agent.model.valobj.SessionContextHolder;
import cn.bugstack.ai.domain.agent.service.IAgentFlowService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentFlowServiceImpl implements IAgentFlowService {

    private final CompiledGraph conversationWorkflow;
    private final IChatRepository chatRepository;
    private final Map<String, Long> sessionConversationMap = new ConcurrentHashMap<>();

    public AgentFlowServiceImpl(CompiledGraph conversationWorkflow,
                                IChatRepository chatRepository) {
        this.conversationWorkflow = conversationWorkflow;
        this.chatRepository = chatRepository;
    }

    @Override
    public AgentResponseEntity process(AgentRequestEntity request) {
        String sessionId = request.getSessionId();
        String text = request.getText();
        log.info("AgentFlow 处理请求: sessionId={}, text={}", sessionId, text);

        SessionContextHolder.setSessionId(sessionId);
        try {
            // 获取或创建会话记录
            Long conversationId = request.getConversationId();
            if (conversationId == null) {
                conversationId = sessionConversationMap.computeIfAbsent(sessionId,
                        sid -> chatRepository.createConversation(sid, "语音会话 " + sid));
            }

            // 从 DB 加载历史消息，转换为 Spring AI Message 对象
            List<ChatMessageEntity> history = chatRepository.getConversationMessages(conversationId);
            List<Message> messages = new ArrayList<>();
            for (ChatMessageEntity msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
            // 追加当前用户输入
            messages.add(new UserMessage(text));

            Map<String, Object> input = new HashMap<>();
            input.put("input", text);
            input.put("sessionId", sessionId);
            input.put("vision_output", "");
            input.put("messages", messages);

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

            // 提取响应内容
            String responseText = null;
            AgentType agentType = AgentType.TEXT;
            String instruction = null;

            var visionOutput = state.value("vision_output");
            if (visionOutput.isPresent()) {
                AgentOutput visionAgentOutput = extractAgentOutput(visionOutput.get());
                if (visionAgentOutput != null && visionAgentOutput.getResponse() != null && !visionAgentOutput.getResponse().isBlank()) {
                    responseText = visionAgentOutput.getResponse();
                    instruction = visionAgentOutput.getInstruction();
                    agentType = AgentType.VISION;
                }
            }

            if (responseText == null) {
                var textOutput = state.value("text_output");
                if (textOutput.isPresent()) {
                    AgentOutput agentOutput = extractAgentOutput(textOutput.get());
                    if (agentOutput != null && agentOutput.getResponse() != null && !agentOutput.getResponse().isBlank()) {
                        responseText = agentOutput.getResponse();
                        instruction = agentOutput.getInstruction();
                    }
                }
            }

            if (responseText == null) {
                log.warn("工作流无法提取有效响应: sessionId={}", sessionId);
                return buildResponse(sessionId, "抱歉，我暂时无法回答这个问题。", AgentType.TEXT);
            }

            // 持久化对话到数据库
            chatRepository.saveMessage(conversationId, "user", text, null);
            chatRepository.saveMessage(conversationId, "assistant", responseText,
                    instruction != null ? "{\"instruction\":\"" + instruction + "\"}" : null);

            // 用首条用户消息更新对话标题
            ChatConversationEntity conv = chatRepository.getConversation(conversationId);
            if (conv != null && "新对话".equals(conv.getTitle())) {
                chatRepository.updateConversationTitle(conversationId, truncate(text, 20));
            }

            log.info("{}: sessionId={}, response={}, instruction={}",
                    agentType == AgentType.VISION ? "视觉处理完成" : "文本输出",
                    sessionId, truncate(responseText, 100), instruction);

            return AgentResponseEntity.builder()
                    .sessionId(sessionId)
                    .response(responseText)
                    .instruction(instruction)
                    .agentType(agentType)
                    .build();

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
