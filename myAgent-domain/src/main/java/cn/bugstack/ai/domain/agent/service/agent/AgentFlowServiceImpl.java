package cn.bugstack.ai.domain.agent.service.agent;

import cn.bugstack.ai.domain.agent.adapter.repository.IChatRepository;
import cn.bugstack.ai.domain.agent.model.entity.AgentOutput;
import cn.bugstack.ai.domain.agent.model.entity.AgentRequestEntity;
import cn.bugstack.ai.domain.agent.model.entity.AgentResponseEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatConversationEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatMessageEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AgentType;
import cn.bugstack.ai.domain.agent.service.IAgentFlowService;
import cn.bugstack.ai.domain.session.model.valobj.SessionContextHolder;
import cn.bugstack.ai.domain.agent.model.entity.CameraFrameEntity;
import cn.bugstack.ai.domain.agent.service.ICameraFrameService;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Slf4j
@Service
public class AgentFlowServiceImpl implements IAgentFlowService {

    private CompiledGraph conversationWorkflow;

    private final ChatModel textChatModel;
    private final ReactAgent textAgent;
    private final ChatClient visionChatClient;
    private final ICameraFrameService cameraFrameService;
    private final IChatRepository chatRepository;
    private final Map<String, Long> sessionConversationMap = new ConcurrentHashMap<>();

    public AgentFlowServiceImpl(@Qualifier("textChatModel") ChatModel textChatModel,
                                @Qualifier("textAgent") ReactAgent textAgent,
                                @Qualifier("visionChatClient") ChatClient visionChatClient,
                                ICameraFrameService cameraFrameService,
                                IChatRepository chatRepository) {
        this.textChatModel = textChatModel;
        this.textAgent = textAgent;
        this.visionChatClient = visionChatClient;
        this.cameraFrameService = cameraFrameService;
        this.chatRepository = chatRepository;
    }

    @PostConstruct
    public void init() throws Exception {
        log.info("初始化 AgentFlow 工作流...");

        NodeAction routerNode = (state) -> {
            String input = state.value("input", "").toString();

            String route = ChatClient.builder(textChatModel).build().prompt()
                    .user(u -> u.text("""
                            判断用户问题是否需要查看摄像头画面。
                            如果问题涉及视觉信息（环境、物体、表情、动作、"这是什么"、"看我"等），回复「vision」。
                            如果是普通聊天、问答、闲聊，回复「text」。
                            如果对话结束，回复「finish」。

                            用户：{input}
                            只需回复一个词：vision / text / finish""")
                            .param("input", input))
                    .call()
                    .content()
                    .trim()
                    .toLowerCase();

            log.info("RouterNode 决策: input={}, route={}", input, route);

            if (!List.of("vision", "text", "finish").contains(route)) {
                route = "text";
            }

            return Map.of("route", route);
        };

        NodeAction visionNode = (state) -> {
            String input = state.value("input", "").toString();
            String sessionId = state.value("sessionId", "").toString();

            log.info("VisionNode 开始处理: sessionId={}", sessionId);

            CameraFrameEntity frame = cameraFrameService.getFrame(sessionId);
            if (frame == null || frame.getImageData() == null) {
                cameraFrameService.clearFrame(sessionId);
                frame = cameraFrameService.waitForFrame(sessionId, 5000);
            }
            if (frame == null || frame.getImageData() == null) {
                return Map.of("vision_output", "抱歉，我无法获取摄像头画面，请确保摄像头已开启。");
            }

            String b64 = Base64.getEncoder().encodeToString(frame.getImageData());
            URI dataUri = new URI("data:image/jpeg;base64," + b64);
            Media media = new Media(MimeTypeUtils.IMAGE_JPEG, dataUri);

            UserMessage userMsg = UserMessage.builder()
                    .text(input + "\n\n请结合摄像头画面内容回答用户的问题。根据画面场景选择合适的语气。\n\n" +
                            "你必须严格按以下 JSON 格式回复（不要包含 markdown 代码块标记）：\n" +
                            "\\{\"response\": \"你的回答文本\", \"instruction\": \"语音合成语气描述\"\\}\n\n" +
                            "其中 instruction 字段可选以下值之一：\n" +
                            "- 用开心兴奋的语气朗读\n" +
                            "- 用低沉悲伤的语气朗读\n" +
                            "- 用温柔关切的语气朗读\n" +
                            "- 用惊讶的语气朗读\n" +
                            "- 用自然温和的语气朗读\n\n" +
                            "只返回 JSON，不要额外说明。")
                    .media(List.of(media))
                    .build();

            String response = visionChatClient.prompt()
                    .messages(userMsg)
                    .call()
                    .content();

            return Map.of("vision_output", response != null ? response : "");
        };

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("input", new ReplaceStrategy());
            strategies.put("route", new ReplaceStrategy());
            strategies.put("vision_output", new ReplaceStrategy());
            strategies.put("messages", new AppendStrategy());
            return strategies;
        };

        StateGraph graph = new StateGraph(keyStrategyFactory);

        graph.addNode("router_node", node_async(routerNode));
        graph.addNode("vision_node", node_async(visionNode));
        graph.addNode("text_agent", textAgent.asNode(true, true));

        graph.addEdge(START, "router_node");
        graph.addConditionalEdges(
                "router_node",
                edge_async(state -> state.value("route", "finish").toString()),
                Map.of("vision", "vision_node", "text", "text_agent", "finish", END)
        );
        graph.addEdge("vision_node", END);
        graph.addEdge("text_agent", END);

        conversationWorkflow = graph.compile(CompileConfig.builder().build());
        log.info("AgentFlow 工作流初始化完成");
    }

    @Override
    public AgentResponseEntity process(AgentRequestEntity request) {
        String sessionId = request.getSessionId();
        String text = request.getText();
        log.info("AgentFlow 处理请求: sessionId={}, text={}", sessionId, text);

        SessionContextHolder.setSessionId(sessionId);
        try {
            Long conversationId = request.getConversationId();
            if (conversationId == null) {
                conversationId = sessionConversationMap.computeIfAbsent(sessionId,
                        sid -> chatRepository.createConversation(sid, "语音会话 " + sid));
            }

            List<ChatMessageEntity> history = chatRepository.getConversationMessages(conversationId);
            List<Message> messages = new ArrayList<>();
            for (ChatMessageEntity msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
            messages.add(new UserMessage(text));

            Map<String, Object> input = new HashMap<>();
            input.put("input", text);
            input.put("sessionId", sessionId);
            input.put("vision_output", "");
            input.put("messages", messages);

            com.alibaba.cloud.ai.graph.NodeOutput lastOutput = conversationWorkflow.stream(input)
                    .doOnNext(output -> {
                        if (output instanceof com.alibaba.cloud.ai.graph.streaming.StreamingOutput<?> streaming) {
                            log.debug("工作流节点: node={}, data={}", streaming.node(), streaming.message());
                        }
                    })
                    .blockLast();

            if (lastOutput == null) {
                log.warn("工作流返回空结果: sessionId={}", sessionId);
                return buildResponse(sessionId, "抱歉，我没有理解您的问题。", AgentType.TEXT);
            }

            var state = lastOutput.state();

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

            chatRepository.saveMessage(conversationId, "user", text, null);
            chatRepository.saveMessage(conversationId, "assistant", responseText,
                    instruction != null ? "{\"instruction\":\"" + instruction + "\"}" : null);

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
        if (json.contains("textContent=")) {
            int start = json.indexOf("textContent=") + "textContent=".length();
            if (start < json.length() && json.charAt(start) == '{') {
                int end = json.indexOf('}', start);
                if (end > start) {
                    json = json.substring(start, end + 1);
                }
            }
        }
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
