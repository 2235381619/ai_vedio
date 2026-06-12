package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraFrameEntity {
    private String sessionId;
    private byte[] imageData;
    private String format;
    private Integer width;
    private Integer height;
    private Long timestamp;

    public boolean hasImage() {
        return imageData != null && imageData.length > 0;
    }
}
