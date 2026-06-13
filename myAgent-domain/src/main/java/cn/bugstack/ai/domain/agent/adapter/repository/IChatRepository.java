package cn.bugstack.ai.domain.agent.adapter.repository;

import cn.bugstack.ai.domain.agent.model.entity.ChatConversationEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatMessageEntity;

import java.util.List;

public interface IChatRepository {

    Long createConversation(String userId, String title);

    void updateConversationTitle(Long conversationId, String title);

    ChatConversationEntity getConversation(Long conversationId);

    List<ChatConversationEntity> getUserConversations(String userId);

    void deleteConversation(Long conversationId);

    Long saveMessage(Long conversationId, String role, String content, String metadata);

    List<ChatMessageEntity> getConversationMessages(Long conversationId);

    void deleteConversationMessages(Long conversationId);
}
