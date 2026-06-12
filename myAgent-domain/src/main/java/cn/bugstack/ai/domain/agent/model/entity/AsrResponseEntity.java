package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrResponseEntity {
    private String sessionId;
    private String text;
    private double confidence;
    private boolean isFinal;
    private List<WordInfo> words;

    public boolean getIsFinal() { return isFinal; }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WordInfo {
        private String word;
        private double startTime;
        private double endTime;
        private double confidence;
    }
}
