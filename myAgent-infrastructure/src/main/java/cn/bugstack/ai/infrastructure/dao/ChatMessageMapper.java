package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.ChatMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    void insert(ChatMessagePO po);

    List<ChatMessagePO> selectByConversationId(@Param("conversationId") Long conversationId);

    void deleteByConversationId(@Param("conversationId") Long conversationId);
}
