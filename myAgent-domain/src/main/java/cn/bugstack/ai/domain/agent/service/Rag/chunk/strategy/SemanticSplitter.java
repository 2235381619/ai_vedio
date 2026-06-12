package cn.bugstack.ai.domain.agent.service.Rag.chunk.strategy;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.Rag.chunk.TextSplitterStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 语义切分策略
 * <p>
 * 先粗切为小窗口，用 embedding 计算相邻窗口的语义相似度，
 * 在相似度低谷处切断，保证每个块的语义完整性。
 * <p>
 * 适用于内容主题会自然变化的长文档（会议纪要、多主题文章）。
 * 效果最好但需要额外调用 embedding API。
 */
@Slf4j
@Component
public class SemanticSplitter implements TextSplitterStrategy {

    // 抽取常量，消除魔法值
    private static final int DEFAULT_PRE_WINDOW_SIZE = 200;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    // 前置窗口重叠比例（设为0，避免重叠内容拉高相似度）
    private static final double PRE_WINDOW_OVERLAP_RATIO = 0.0;

    private final EmbeddingModel embeddingModel;
    // 复用分割器（无状态，单例安全）
    private final RecursiveCharacterSplitter characterSplitter = new RecursiveCharacterSplitter();

    public SemanticSplitter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String getStrategyName() {
        return "semantic";
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return split(documents, SplitterConfig.defaults());
    }

    /**
     * 带自定义配置的语义分块入口
     */
    @Override
    public List<Document> split(List<Document> documents, SplitterConfig config) {
        // 1. 前置粗分：生成滑动小窗口（带重叠）
        List<Document> windows = preSplit(documents, config);
        if (windows.size() <= 1) {
            fillBaseMetadata(windows);
            return windows;
        }

        // 2. 批量生成向量（增加空值/异常保护）
        List<float[]> embeddings = batchEmbed(windows);
        if (embeddings.size() != windows.size()) {
            return windows;
        }

        // 3. 计算相邻窗口相似度
        List<Double> similarities = calcSimilarities(embeddings);
        double threshold = getValidThreshold(config);

        // 4. 基于相似度划分语义分组
        List<List<Document>> semanticGroups = groupBySimilarity(windows, similarities, threshold);

        // 5. 合并语义组 + 二次细分 + 构建最终结果
        List<Document> result = mergeAndSubSplit(semanticGroups, config);

        // 6. 统一全局索引 & 总块数（仅最后赋值一次，不再重复覆盖）
        fillGlobalIndexAndTotal(result);
        return result;
    }

    /**
     * 前置字符粗分（带重叠窗口）
     */
    private List<Document> preSplit(List<Document> documents, SplitterConfig config) {
        SplitterConfig preConfig = new SplitterConfig();
        int preWindowSize = config.getBufferSize() <= 0 ? DEFAULT_PRE_WINDOW_SIZE : config.getBufferSize();
        preConfig.setChunkSize(preWindowSize);
        // 计算重叠大小，不再硬设为0
        int overlap = (int) (preWindowSize * PRE_WINDOW_OVERLAP_RATIO);
        preConfig.setOverlap(overlap);
        return characterSplitter.split(documents, preConfig);
    }

    /**
     * 批量向量化 + 空值/异常保护
     */
    private List<float[]> batchEmbed(List<Document> windows) {
        List<float[]> embeddings = new ArrayList<>(windows.size());
        for (Document window : windows) {
            String text = window.getText();
            if (text == null || text.isBlank()) {
                embeddings.add(new float[0]);
                continue;
            }
            float[] embedding;
            try {
                embedding = embeddingModel.embed(text);
            } catch (Exception e) {
                log.error("embedding 调用失败: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
            embeddings.add(embedding == null ? new float[0] : embedding);
        }
        return embeddings;
    }

    /**
     * 计算相邻向量余弦相似度
     */
    private List<Double> calcSimilarities(List<float[]> embeddings) {
        List<Double> similarities = new ArrayList<>(embeddings.size() - 1);
        for (int i = 0; i < embeddings.size() - 1; i++) {
            float[] vecA = embeddings.get(i);
            float[] vecB = embeddings.get(i + 1);
            similarities.add(cosineSimilarity(vecA, vecB));
        }
        return similarities;
    }

    /**
     * 解析合法相似度阈值（支持负数/0）
     */
    private double getValidThreshold(SplitterConfig config) {
        double threshold = config.getSimilarityThreshold();
        // 仅当未配置/非法值时使用默认值，不再强制覆盖0/负数
        return threshold < -1 || threshold > 1 ? DEFAULT_SIMILARITY_THRESHOLD : threshold;
    }

    /**
     * 根据相似度划分语义分组
     */
    private List<List<Document>> groupBySimilarity(List<Document> windows,
                                                   List<Double> similarities,
                                                   double threshold) {
        List<List<Document>> groups = new ArrayList<>();
        List<Document> currentGroup = new ArrayList<>();
        currentGroup.add(windows.get(0));

        log.info("语义切分相似度明细 (阈值={}):", String.format("%.2f", threshold));
        for (int i = 0; i < similarities.size(); i++) {
            currentGroup.add(windows.get(i + 1));
            log.info("  窗口{}-{} 相似度: {}{}",
                    i, i + 1,
                    String.format("%.4f", similarities.get(i)),
                    similarities.get(i) < threshold ? " ← 切断" : "");
            if (similarities.get(i) < threshold) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }
        }
        log.info("语义切分结果: {} 个分组", groups.size() + (currentGroup.isEmpty() ? 0 : 1));
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        return groups;
    }

    /**
     * 合并语义组 + 超长二次细分 + 填充基础元数据
     */
    private List<Document> mergeAndSubSplit(List<List<Document>> semanticGroups, SplitterConfig config) {
        List<Document> result = new ArrayList<>();
        int targetChunkSize = config.getChunkSize();

        for (List<Document> group : semanticGroups) {
            // 合并同语义组文本（可扩展分隔符，此处保留原逻辑，建议后续改为可配置）
            String mergedText = group.stream()
                    .map(doc -> doc.getText() == null ? "" : doc.getText())
                    .collect(Collectors.joining("\n"));

            Document firstDoc = group.get(0);
            Document mergedChunk = new Document(mergedText, new HashMap<>(firstDoc.getMetadata()));
            // 统一基础元数据（直接put，保证策略准确）
            mergedChunk.getMetadata().put("strategy", getStrategyName());
            mergedChunk.getMetadata().put("semantic_windows", group.size());

            // 优化超长判断：超过目标块大小就二次切分，不再 *2
            if (targetChunkSize > 0 && mergedText.length() > targetChunkSize) {
                List<Document> subChunks = characterSplitter.split(List.of(mergedChunk), config);
                // 子块继承语义窗口数、切分策略
                for (Document sub : subChunks) {
                    sub.getMetadata().put("strategy", getStrategyName());
                    sub.getMetadata().put("semantic_windows", group.size());
                    result.add(sub);
                }
            } else {
                result.add(mergedChunk);
            }
        }
        return result;
    }

    /**
     * 统一填充全局索引 & 总块数（只执行一次，修复索引覆盖Bug）
     */
    private void fillGlobalIndexAndTotal(List<Document> result) {
        int total = result.size();
        for (int i = 0; i < total; i++) {
            Document doc = result.get(i);
            doc.getMetadata().put("chunk_index", i);
            doc.getMetadata().put("total_chunks", total);
        }
    }

    /**
     * 少量窗口时填充基础元数据
     */
    private void fillBaseMetadata(List<Document> windows) {
        for (Document win : windows) {
            win.getMetadata().put("strategy", getStrategyName());
        }
    }

    /**
     * 余弦相似度计算（增加向量维度校验、零向量保护）
     */
    private double cosineSimilarity(float[] a, float[] b) {
        // 空向量直接返回0
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
        // 向量维度不一致，返回最小相似度
        if (a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        // 零向量保护
        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return dotProduct / denominator;
    }
}