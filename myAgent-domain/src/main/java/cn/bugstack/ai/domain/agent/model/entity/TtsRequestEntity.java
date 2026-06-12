package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsRequestEntity {
    private String sessionId;
    private String text;
    private String voice;
    private String languageType;
    private String mode;
    private Float speechRate;
    private Integer volume;
    private Float pitchRate;
}
