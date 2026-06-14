package cn.bugstack.ai.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .defaultOptions(DashScopeChatOptions.builder().withModel("qwen-plus").build())
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
    public ReactAgent textAgent(@Qualifier("textChatModel") ChatModel textChatModel,
                                @Autowired(required = false) ToolCallbackProvider toolCallbackProvider) {
        // 注意：这里不要设置 .outputType()/强制 JSON 输出。
        // outputType 会被 BeanOutputConverter 转成 JSON Schema，并在 ReAct 循环的“每一轮”模型调用中
        // 追加到用户消息末尾，叠加“只返回 JSON”的约束会让模型直接产出最终 JSON、不再发出 tool_calls，
        // 从而导致工具调用循环被提前终止（例如“规划路线”只说了句要查坐标就结束）。
        // 让本 Agent 专注“工具推理 + 自然语言回答”，最终的 {response, instruction} 整形交给后续步骤完成。
        return ReactAgent.builder()
                .name("text_agent")
                .model(textChatModel)
                .instruction("你是一个友好的语音对话助手，正在和用户进行轻松的语音聊天。" +
                        "\n\n" +
                        "用户说的是：{input}\n\n" +
                        "工具使用原则（非常重要）：\n" +
                        "- 默认不要使用任何工具。日常聊天、情绪倾诉、安慰鼓励、常识问答、建议看法等，直接用你自己的知识回答，绝不要调用工具。\n" +
                        "- 只有当用户在这句话里【明确】要查询实时信息时，才调用对应工具，例如：明确问“现在几点/今天星期几”用时间工具；明确问“某地天气”用天气工具；" +
                        "明确要“从A到B怎么走/路线/导航/有多远”用地理坐标和路线规划工具；明确要“附近的XX”用 POI 搜索工具。\n" +
                        "- 用户没有明确提出这类查询时，哪怕话题里顺带提到了地点、时间、心情等，也不要主动去查天气、时间或路线。\n" +
                        "- 需要外部数据时不要凭空编造坐标、距离、路线、时间等。\n\n" +
                        "回复要求：\n" +
                        "- 用自然、口语化的中文，简洁一些，适合语音朗读。\n" +
                        "- 不要使用 markdown、标题、列表符号（如 - 、1. ）或表情符号，用连贯的句子表达。")
                .outputKey("text_output")
                .toolCallbackProviders(toolCallbackProvider)
                .build();

    }

    @Bean("visionChatClient")
    public ChatClient visionChatClient(@Qualifier("visionChatModel") ChatModel visionChatModel) {
        return ChatClient.builder(visionChatModel).build();
    }
}
