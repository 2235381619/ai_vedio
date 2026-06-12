package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewRequestDTO {

    /** 原始文本内容 */
    private String content;

    /** 切片策略名称 */
    private String splitterName;

    /** 目标块大小（token 数） */
    private Integer chunkSize;

    /** 重叠大小 */
    private Integer overlap;

    /** Markdown 标题层级 */
    private List<String> headingLevels;

    /** 语义相似度阈值 */
    private Double similarityThreshold;

    /** 语义缓冲区大小 */
    private Integer bufferSize;

    /** 自定义分隔符 */
    private List<String> separators;

}
