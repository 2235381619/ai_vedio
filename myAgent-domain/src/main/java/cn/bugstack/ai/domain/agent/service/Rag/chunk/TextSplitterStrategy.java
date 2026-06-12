package cn.bugstack.ai.domain.agent.service.Rag.chunk;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.List;

/**
 * 文本切片策略接口
 * <p>
 * 继承 Spring AI 的 {@link DocumentTransformer}，兼容 ETL Pipeline 链式调用。
 * 每个策略实现一种文档分块算法，通过 {@link #getStrategyName()} 区分。
 */
public interface TextSplitterStrategy extends DocumentTransformer {

    /** 策略唯一标识，用于配置选择和路由 */
    String getStrategyName();

    /**
     * 带配置的切片，供 previewChunk 等场景使用。
     * 各策略自行实现，确保 config 参数真正生效。
     */
    List<Document> split(List<Document> documents, SplitterConfig config);

    @Override
    List<Document> apply(List<Document> documents);

}
