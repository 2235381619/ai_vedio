package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResultDTO {

    /** 切片内容 */
    private String content;

    /** 元数据（策略名、序号、标题链等） */
    private Map<String, Object> metadata;

}
