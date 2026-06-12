package cn.bugstack.ai.domain.agent.service.Rag.chunk;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档切片服务 — 仅负责切片策略相关操作
 */
public interface IChunkService {



    /**
     * 对 Document 列表执行切片
     *
     * @param documents     输入文档
     * @param splitterName  切片策略名称
     * @return 切分后的文档块列表
     */
    List<Document> split(List<Document> documents, String splitterName);


}
