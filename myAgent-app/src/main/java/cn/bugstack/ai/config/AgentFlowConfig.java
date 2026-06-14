package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.model.entity.AgentOutput;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

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
    public ReactAgent textAgent(@Qualifier("textChatModel") ChatModel textChatModel) {
        return ReactAgent.builder()
                .name("text_agent")
                .model(textChatModel)
                .instruction("你是一个友好的语音对话助手。请根据用户说的话，生成回复文本和朗读语气。\n\n" +
                        "用户说的是：{input}\n\n" +
                        "你必须严格按以下 JSON 格式回复（不要包含 markdown 代码块标记）：\n" +
                        "\\{\"response\": \"你的回复内容\", \"instruction\": \"语气描述\"\\}\n\n" +
                        "其中 instruction 字段可选以下值之一：\n" +
                        "- 用开心兴奋的语气朗读\n" +
                        "- 用低沉悲伤的语气朗读\n" +
                        "- 用温柔关切的语气朗读\n" +
                        "- 用惊讶的语气朗读\n" +
                        "- 用自然温和的语气朗读\n\n" +
                        "回复内容必须针对用户刚才说的话。\n" +
                        "只返回 JSON，不要额外说明。")
                .outputType(AgentOutput.class)
                .outputKey("text_output")
                .build();
    }

    @Bean("visionChatClient")
    public ChatClient visionChatClient(@Qualifier("visionChatModel") ChatModel visionChatModel) {
        return ChatClient.builder(visionChatModel).build();
    }
}
