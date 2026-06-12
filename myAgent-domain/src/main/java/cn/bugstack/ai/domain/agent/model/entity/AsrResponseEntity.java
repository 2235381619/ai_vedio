package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrResponseEntity {
    private String text;
    private double confidence;
    private boolean isFinal;

    public boolean getIsFinal() { return isFinal; }
}
