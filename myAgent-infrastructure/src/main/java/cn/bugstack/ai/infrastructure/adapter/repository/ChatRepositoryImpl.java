package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.agent.adapter.repository.IChatRepository;
import cn.bugstack.ai.domain.agent.model.entity.ChatConversationEntity;
import cn.bugstack.ai.domain.agent.model.entity.ChatMessageEntity;
import cn.bugstack.ai.infrastructure.dao.ChatConversationMapper;
import cn.bugstack.ai.infrastructure.dao.ChatMessageMapper;
import cn.bugstack.ai.infrastructure.dao.po.ChatConversationPO;
import cn.bugstack.ai.infrastructure.dao.po.ChatMessagePO;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ChatRepositoryImpl implements IChatRepository {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;

    public ChatRepositoryImpl(ChatConversationMapper conversationMapper,
                              ChatMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public Long createConversation(String userId, String title) {
        ChatConversationPO po = new ChatConversationPO();
        po.setTitle(title != null ? title : "新对话");
        po.setUserId(userId);
        po.setStatus(0);
        po.setCreatedAt(new Date());
        po.setUpdatedAt(new Date());
        conversationMapper.insert(po);
        return po.getId();
    }

    @Override
    public void updateConversationTitle(Long conversationId, String title) {
        ChatConversationPO po = new ChatConversationPO();
        po.setId(conversationId);
        po.setTitle(title);
        po.setUpdatedAt(new Date());
        conversationMapper.updateById(po);
    }

    @Override
    public ChatConversationEntity getConversation(Long conversationId) {
        ChatConversationPO po = conversationMapper.selectById(conversationId);
        return po != null ? toConversationEntity(po) : null;
    }

    @Override
    public List<ChatConversationEntity> getUserConversations(String userId) {
        return conversationMapper.selectByUserId(userId).stream()
                .map(this::toConversationEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteConversation(Long conversationId) {
        conversationMapper.deleteById(conversationId);
        messageMapper.deleteByConversationId(conversationId);
    }

    @Override
    public Long saveMessage(Long conversationId, String role, String content, String metadata) {
        ChatMessagePO po = new ChatMessagePO();
        po.setConversationId(conversationId);
        po.setRole(role);
        po.setContent(content);
        po.setMetadata(metadata);
        po.setCreatedAt(new Date());
        messageMapper.insert(po);
        return po.getId();
    }

    @Override
    public List<ChatMessageEntity> getConversationMessages(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId).stream()
                .map(this::toMessageEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteConversationMessages(Long conversationId) {
        messageMapper.deleteByConversationId(conversationId);
    }

    private ChatConversationEntity toConversationEntity(ChatConversationPO po) {
        ChatConversationEntity entity = new ChatConversationEntity();
        entity.setId(po.getId());
        entity.setTitle(po.getTitle());
        entity.setUserId(po.getUserId());
        entity.setStatus(po.getStatus());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    private ChatMessageEntity toMessageEntity(ChatMessagePO po) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(po.getId());
        entity.setConversationId(po.getConversationId());
        entity.setRole(po.getRole());
        entity.setContent(po.getContent());
        entity.setMetadata(po.getMetadata());
        entity.setCreatedAt(po.getCreatedAt());
        return entity;
    }
}
