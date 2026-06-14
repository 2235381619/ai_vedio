package cn.bugstack.ai.domain.agent.service.rag.chunk.strategy;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.rag.chunk.TextSplitterStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SemanticSplitter implements TextSplitterStrategy {

    private static final int DEFAULT_PRE_WINDOW_SIZE = 200;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    private static final double PRE_WINDOW_OVERLAP_RATIO = 0.0;

    private final EmbeddingModel embeddingModel;
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

    @Override
    public List<Document> split(List<Document> documents, SplitterConfig config) {
        List<Document> windows = preSplit(documents, config);
        if (windows.size() <= 1) {
            fillBaseMetadata(windows);
            return windows;
        }

        List<float[]> embeddings = batchEmbed(windows);
        if (embeddings.size() != windows.size()) {
            return windows;
        }

        List<Double> similarities = calcSimilarities(embeddings);
        double threshold = getValidThreshold(config);

        List<List<Document>> semanticGroups = groupBySimilarity(windows, similarities, threshold);

        List<Document> result = mergeAndSubSplit(semanticGroups, config);

        fillGlobalIndexAndTotal(result);
        return result;
    }

    private List<Document> preSplit(List<Document> documents, SplitterConfig config) {
        SplitterConfig preConfig = new SplitterConfig();
        int preWindowSize = config.getBufferSize() <= 0 ? DEFAULT_PRE_WINDOW_SIZE : config.getBufferSize();
        preConfig.setChunkSize(preWindowSize);
        int overlap = (int) (preWindowSize * PRE_WINDOW_OVERLAP_RATIO);
        preConfig.setOverlap(overlap);
        return characterSplitter.split(documents, preConfig);
    }

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

    private List<Double> calcSimilarities(List<float[]> embeddings) {
        List<Double> similarities = new ArrayList<>(embeddings.size() - 1);
        for (int i = 0; i < embeddings.size() - 1; i++) {
            float[] vecA = embeddings.get(i);
            float[] vecB = embeddings.get(i + 1);
            similarities.add(cosineSimilarity(vecA, vecB));
        }
        return similarities;
    }

    private double getValidThreshold(SplitterConfig config) {
        double threshold = config.getSimilarityThreshold();
        return threshold < -1 || threshold > 1 ? DEFAULT_SIMILARITY_THRESHOLD : threshold;
    }

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

    private List<Document> mergeAndSubSplit(List<List<Document>> semanticGroups, SplitterConfig config) {
        List<Document> result = new ArrayList<>();
        int targetChunkSize = config.getChunkSize();

        for (List<Document> group : semanticGroups) {
            String mergedText = group.stream()
                    .map(doc -> doc.getText() == null ? "" : doc.getText())
                    .collect(Collectors.joining("\n"));

            Document firstDoc = group.get(0);
            Document mergedChunk = new Document(mergedText, new HashMap<>(firstDoc.getMetadata()));
            mergedChunk.getMetadata().put("strategy", getStrategyName());
            mergedChunk.getMetadata().put("semantic_windows", group.size());

            if (targetChunkSize > 0 && mergedText.length() > targetChunkSize) {
                List<Document> subChunks = characterSplitter.split(List.of(mergedChunk), config);
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

    private void fillGlobalIndexAndTotal(List<Document> result) {
        int total = result.size();
        for (int i = 0; i < total; i++) {
            Document doc = result.get(i);
            doc.getMetadata().put("chunk_index", i);
            doc.getMetadata().put("total_chunks", total);
        }
    }

    private void fillBaseMetadata(List<Document> windows) {
        for (Document win : windows) {
            win.getMetadata().put("strategy", getStrategyName());
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
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

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return dotProduct / denominator;
    }
}
