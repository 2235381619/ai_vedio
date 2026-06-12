package cn.bugstack.ai.domain.agent.service.Rag.chunk.strategy;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.Rag.chunk.TextSplitterStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定 Token 数切分策略（包装 Spring AI 的 TokenTextSplitter）
 * <p>
 * 使用 CL100K_BASE 编码精确按 token 数切分。
 * 如果内容本身很小，不会强行切分。
 * 适用于通用文本、无明显结构的文档，是兜底策略。
 */
@Component
public class FixedTokenSplitter implements TextSplitterStrategy {

    private static final List<Character> DEFAULT_PUNCTUATION = List.of('.', '?', '!', '\n', '。', '！', '？');

    private final TokenTextSplitter delegate;

    public FixedTokenSplitter() {
        this.delegate = new TokenTextSplitter();
    }

    public FixedTokenSplitter(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed,
                              int maxNumChunks, boolean keepSeparator, List<Character> punctuationMarks) {
        this.delegate = new TokenTextSplitter(chunkSize, minChunkSizeChars, minChunkLengthToEmbed,
                maxNumChunks, keepSeparator, punctuationMarks);
    }

    @Override
    public String getStrategyName() {
        return "fixed-token";
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return delegate.apply(documents);
    }

    @Override
    public List<Document> split(List<Document> documents, SplitterConfig config) {
        TokenTextSplitter tokenSplitter = new TokenTextSplitter(
                config.getChunkSize(), 350, 5, 10000, true, DEFAULT_PUNCTUATION);
        return tokenSplitter.apply(documents);
    }

}
