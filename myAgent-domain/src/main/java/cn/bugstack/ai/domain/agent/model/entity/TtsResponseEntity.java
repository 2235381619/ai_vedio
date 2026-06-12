package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsResponseEntity {
    private String sessionId;
    private byte[] audioData;
    private String format;
    private int sampleRate;
    private String text;
    private long firstAudioDelay;
}
