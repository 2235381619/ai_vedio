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
public class ChatMessageEntity {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String metadata;
    private Date createdAt;
}
