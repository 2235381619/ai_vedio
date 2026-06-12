package cn.bugstack.ai.config;

import cn.bugstack.ai.tools.VisionTool;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Slf4j
@Configuration
public class AgentFlowConfig {

    @Bean("dashScopeApi")
    public DashScopeApi dashScopeApi(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    @Bean("textChatModel")
    public ChatModel textChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().withModel("qwen-turbo").build())
                .build();
    }

    @Bean("visionChatModel")
    public ChatModel visionChatModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().withModel("qwen-vl-max").withMultiModel(true).build())
                .build();
    }

    @Bean("textAgent")
    public ReactAgent textAgent(@Qualifier("textChatModel") ChatModel textChatModel) {
        return ReactAgent.builder()
                .name("text_agent")
                .model(textChatModel)
                .instruction("你是一个友好的语音对话助手。请根据用户输入给出自然、流畅、简洁的口语化回复。")
                .outputKey("text_output")
                .build();
    }

    @Bean("visionChatClient")
    public ChatClient visionChatClient(@Qualifier("visionChatModel") ChatModel visionChatModel) {
        return ChatClient.builder(visionChatModel).build();
    }

    @Bean("conversationWorkflow")
    public CompiledGraph conversationWorkflow(
            @Qualifier("textChatModel") ChatModel textChatModel,
            @Qualifier("textAgent") ReactAgent textAgent,
            @Qualifier("visionChatClient") ChatClient visionChatClient,
            VisionTool visionTool) throws Exception {

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

            var frame = visionTool.captureFrame(sessionId);
            if (frame == null || !frame.hasImage()) {
                return Map.of("vision_output", "抱歉，我无法获取摄像头画面，请确保摄像头已开启。");
            }

            String b64 = Base64.getEncoder().encodeToString(frame.getImageData());
            URI dataUri = new URI("data:image/jpeg;base64," + b64);
            Media media = new Media(MimeTypeUtils.IMAGE_JPEG, dataUri);

            UserMessage userMsg = UserMessage.builder()
                    .text(input)
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
        graph.addNode("text_agent", textAgent.asNode(true, false));

        graph.addEdge(START, "router_node");
        graph.addConditionalEdges(
                "router_node",
                edge_async(state -> state.value("route", "finish").toString()),
                Map.of("vision", "vision_node", "text", "text_agent", "finish", END)
        );
        graph.addEdge("vision_node", END);
        graph.addEdge("text_agent", END);

        return graph.compile(CompileConfig.builder().build());
    }
}
