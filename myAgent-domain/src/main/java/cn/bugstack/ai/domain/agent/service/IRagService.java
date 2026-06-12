package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import org.springframework.ai.document.Document;

import java.io.InputStream;
import java.util.List;

public interface IRagService {

    /**
     * 上传文档并写入知识库
     */
    void uploadFile(InputStream inputStream, String docName, String splitterName);

    /**
     * 预览切片效果
     */
    List<Document> previewChunk(String content, String splitterName, SplitterConfig config);

    /**
     * 查看所有可用的切片策略
     */
    List<String> listStrategies();

}
