package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatConversationEntity {
    private Long id;
    private String title;
    private String userId;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;

}
