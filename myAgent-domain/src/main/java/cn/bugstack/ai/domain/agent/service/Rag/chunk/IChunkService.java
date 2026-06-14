package cn.bugstack.ai.domain.agent.service.rag.chunk;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import org.springframework.ai.document.Document;

import java.util.List;

public interface IChunkService {

    List<Document> split(List<Document> documents, String splitterName);

}
