package cn.bugstack.ai.test;

import cn.bugstack.ai.domain.agent.adapter.repository.IChatRepository;
import cn.bugstack.ai.domain.agent.model.entity.ChatConversationEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatMessageEntity;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ChatRepositoryTest {

    @Autowired
    private IChatRepository chatRepository;

    @Test
    public void test_saveAndQuery() {
        // 1. 创建会话
        Long conversationId = chatRepository.createConversation("test_user", "测试对话");
        log.info("创建会话 ID: {}", conversationId);

        // 2. 插入用户消息
        chatRepository.saveMessage(conversationId, "user", "你好，今天天气怎么样？", null);

        // 3. 插入 AI 回复
        chatRepository.saveMessage(conversationId, "assistant",
                "今天天气很好，适合出门散步。", "{\"tokens\": 42}");

        // 4. 查询会话
        ChatConversationEntity conversation = chatRepository.getConversation(conversationId);
        log.info("会话信息: id={}, title={}, status={}", conversation.getId(), conversation.getTitle(), conversation.getStatus());

        // 5. 查询消息列表
        List<ChatMessageEntity> messages = chatRepository.getConversationMessages(conversationId);
        log.info("消息数量: {}", messages.size());
        for (ChatMessageEntity msg : messages) {
            log.info("消息: role={}, content={}", msg.getRole(), msg.getContent());
        }

        // 6. 查询用户会话列表
        List<ChatConversationEntity> conversations = chatRepository.getUserConversations("test_user");
        log.info("用户会话数量: {}", conversations.size());
    }
}
