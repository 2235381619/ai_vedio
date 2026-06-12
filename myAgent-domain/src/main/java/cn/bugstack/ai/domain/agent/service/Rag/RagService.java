package cn.bugstack.ai.domain.agent.service.Rag;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.IRagService;
import cn.bugstack.ai.domain.agent.service.Rag.chunk.TextSplitterContext;
import cn.bugstack.ai.domain.agent.service.Rag.chunk.TextSplitterStrategy;
import cn.bugstack.ai.domain.agent.service.Rag.chunk.strategy.FixedTokenSplitter;
import com.alibaba.cloud.ai.parser.tika.TikaDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
public class RagService implements IRagService {

    private final TextSplitterContext splitterContext;
    private final VectorStore vectorStore;
    private final TikaDocumentParser parser = new TikaDocumentParser();

    public RagService(TextSplitterContext splitterContext, VectorStore vectorStore) {
        this.splitterContext = splitterContext;
        this.vectorStore = vectorStore;
    }

    @Override
    public void uploadFile(InputStream inputStream, String docName, String splitterName) {
        log.info("开始上传文档: docName={}, splitter={}", docName, splitterName);

        // 1. 解析文档
        List<Document> documents = parser.parse(inputStream);
        documents.forEach(doc -> doc.getMetadata().put("source", docName));
        log.info("解析完成: {} 个文档块", documents.size());

        // 2. 切片
        List<Document> chunks = splitterContext.split(splitterName, documents);
        log.info("切片完成: {} 个切片块", chunks.size());

        // 3. 写入向量库（内部自动 embedding）
        vectorStore.write(chunks);
        log.info("入库完成: 共 {} 个切片块写入 PGVector", chunks.size());
    }

    @Override
    public List<Document> previewChunk(String content, String splitterName, SplitterConfig config) {
        Document doc = new Document(content);
        TextSplitterStrategy strategy = splitterContext.getStrategy(splitterName);
        return strategy.split(List.of(doc), config);
    }

    @Override
    public List<String> listStrategies() {
        return splitterContext.listStrategies();
    }

}
