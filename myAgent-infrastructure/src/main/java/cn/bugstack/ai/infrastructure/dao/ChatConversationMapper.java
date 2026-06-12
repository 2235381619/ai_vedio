package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.ChatConversationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatConversationMapper {

    void insert(ChatConversationPO po);

    void updateById(ChatConversationPO po);

    ChatConversationPO selectById(@Param("id") Long id);

    List<ChatConversationPO> selectByUserId(@Param("userId") String userId);

    void deleteById(@Param("id") Long id);
}
