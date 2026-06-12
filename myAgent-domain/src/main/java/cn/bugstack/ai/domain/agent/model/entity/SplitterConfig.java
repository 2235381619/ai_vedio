package cn.bugstack.ai.domain.agent.model.entity;

import lombok.*;

import java.util.List;

/**
 * 切片策略通用配置
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SplitterConfig {

    /** 目标块大小（近似 token 数） */
    private int chunkSize = 500;

    /** 相邻块重叠大小 */
    private int overlap = 50;

    // ===== MarkdownHeaderSplitter 专用 =====
    private List<String> headingLevels;

    // ===== SemanticSplitter 专用 =====
    private double similarityThreshold = 0.7;
    private int bufferSize = 200;

    // ===== RecursiveCharacterSplitter 专用 =====
    private List<String> separators;

    public static SplitterConfig defaults() {
        return new SplitterConfig();
    }

}