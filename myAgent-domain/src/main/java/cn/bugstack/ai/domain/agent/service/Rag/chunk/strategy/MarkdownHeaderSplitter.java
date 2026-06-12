package cn.bugstack.ai.domain.agent.service.Rag.chunk.strategy;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.Rag.chunk.TextSplitterStrategy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Markdown 标题切分策略
 * <p>
 * 按 Markdown 标题层级（# ## ###）分块，保留标题层级结构到 metadata。
 * 检索时可以带上标题上下文，让 LLM 知道内容所属的章节。
 * <p>
 * 适用于 Markdown 格式的技术文档、API 文档、Wiki 页面。
 */
@Component
public class MarkdownHeaderSplitter implements TextSplitterStrategy {

    private static final List<String> DEFAULT_HEADING_LEVELS = List.of("#", "##", "###", "####", "#####", "######");
    private static final int CHARS_PER_TOKEN = 2;

    @Override
    public String getStrategyName() {
        return "markdown-header";
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return split(documents, SplitterConfig.defaults());
    }

    /**
     * 带配置的切分
     */
    @Override
    public List<Document> split(List<Document> documents, SplitterConfig config) {
        List<String> headingLevels = config.getHeadingLevels() != null && !config.getHeadingLevels().isEmpty()
                ? config.getHeadingLevels() : DEFAULT_HEADING_LEVELS;

        List<Document> result = new ArrayList<>();
        int globalIdx = 0;

        for (Document doc : documents) {
            List<HeadingGroup> groups = parseHeadingGroups(doc.getText(), headingLevels);
            int chunkChars = config.getChunkSize() * CHARS_PER_TOKEN;

            for (HeadingGroup group : groups) {
                if (config.getChunkSize() > 0 && group.content.length() > chunkChars) {
                    RecursiveCharacterSplitter subSplitter = new RecursiveCharacterSplitter();
                    Document tempDoc = new Document(group.content, new HashMap<>(doc.getMetadata()));
                    tempDoc.getMetadata().putAll(group.metadata);
                    List<Document> subChunks = subSplitter.split(List.of(tempDoc), config);
                    for (Document sub : subChunks) {
                        sub.getMetadata().put("strategy", getStrategyName());
                        sub.getMetadata().put("chunk_index", globalIdx++);
                        result.add(sub);
                    }
                } else {
                    Document chunk = new Document(group.content, new HashMap<>(doc.getMetadata()));
                    chunk.getMetadata().putAll(group.metadata);
                    chunk.getMetadata().putIfAbsent("strategy", getStrategyName());
                    chunk.getMetadata().put("chunk_index", globalIdx++);
                    result.add(chunk);
                }
            }
        }

        int total = result.size();
        for (Document chunk : result) {
            chunk.getMetadata().put("total_chunks", total);
        }
        return result;
    }

    private List<HeadingGroup> parseHeadingGroups(String markdown, List<String> headingLevels) {
        List<HeadingGroup> groups = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        Map<Integer, String> currentHeadings = new HashMap<>();
        StringBuilder currentContent = new StringBuilder();
        String currentHeadingText = null;

        for (String line : lines) {
            String headingInfo = matchHeading(line, headingLevels);
            if (headingInfo != null) {
                if (currentContent.length() > 0 || currentHeadingText != null) {
                    String content = currentContent.toString().trim();
                    if (!content.isEmpty()) {
                        groups.add(buildGroup(currentHeadingText, content, new HashMap<>(currentHeadings)));
                    }
                }
                String[] parts = headingInfo.split(":", 2);
                String levelStr = parts[0];
                String headingText = parts.length > 1 ? parts[1].trim() : "";
                int level = levelStr.length();
                currentHeadingText = headingText;
                currentHeadings.entrySet().removeIf(e -> e.getKey() >= level);
                currentHeadings.put(level, headingText);
                currentContent = new StringBuilder();
            } else {
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
            }
        }

        if (currentContent.length() > 0 || currentHeadingText != null) {
            String content = currentContent.toString().trim();
            if (!content.isEmpty()) {
                groups.add(buildGroup(currentHeadingText, content, new HashMap<>(currentHeadings)));
            }
        }

        return groups;
    }

    private String matchHeading(String line, List<String> headingLevels) {
        String trimmed = line.trim();
        for (String level : headingLevels) {
            if (trimmed.startsWith(level + " ")) {
                String headingText = trimmed.substring(level.length()).trim();
                return level + ":" + headingText;
            }
        }
        return null;
    }

    private HeadingGroup buildGroup(String headingText, String content, Map<Integer, String> headings) {
        HeadingGroup group = new HeadingGroup();
        group.content = content;
        for (Map.Entry<Integer, String> entry : headings.entrySet()) {
            group.metadata.put("h" + entry.getKey(), entry.getValue());
        }
        if (headingText != null) {
            group.metadata.put("heading", headingText);
        }
        return group;
    }

    private static class HeadingGroup {
        String content;
        Map<String, Object> metadata = new HashMap<>();
    }

}
