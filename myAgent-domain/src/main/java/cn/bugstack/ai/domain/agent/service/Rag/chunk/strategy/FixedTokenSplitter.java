package cn.bugstack.ai.domain.agent.service.rag.chunk.strategy;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.rag.chunk.TextSplitterStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

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
