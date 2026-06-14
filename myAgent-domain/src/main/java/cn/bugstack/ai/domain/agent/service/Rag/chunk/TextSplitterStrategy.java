package cn.bugstack.ai.domain.agent.service.rag.chunk;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.List;

public interface TextSplitterStrategy extends DocumentTransformer {

    String getStrategyName();

    List<Document> split(List<Document> documents, SplitterConfig config);

    @Override
    List<Document> apply(List<Document> documents);

}
