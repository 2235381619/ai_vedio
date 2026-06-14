package cn.bugstack.ai.domain.agent.service.rag.chunk.strategy;

import cn.bugstack.ai.domain.agent.model.entity.SplitterConfig;
import cn.bugstack.ai.domain.agent.service.rag.chunk.TextSplitterStrategy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RecursiveCharacterSplitter implements TextSplitterStrategy {

    private static final List<String> DEFAULT_SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", "；", "，", " ", ""
    );

    private static final int CHARS_PER_TOKEN = 2;

    @Override
    public String getStrategyName() {
        return "recursive";
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return split(documents, SplitterConfig.defaults());
    }

    @Override
    public List<Document> split(List<Document> documents, SplitterConfig config) {
        List<Document> result = new ArrayList<>();
        List<String> separators = config.getSeparators() != null && !config.getSeparators().isEmpty()
                ? config.getSeparators() : DEFAULT_SEPARATORS;

        int globalIdx = 0;
        for (Document doc : documents) {
            List<String> chunks = recursiveSplit(doc.getText(), separators, 0, config);

            for (String chunkText : chunks) {
                Document chunk = new Document(chunkText, new HashMap<>(doc.getMetadata()));
                chunk.getMetadata().putIfAbsent("strategy", getStrategyName());
                chunk.getMetadata().put("chunk_index", globalIdx++);
                result.add(chunk);
            }
        }

        int total = result.size();
        for (Document chunk : result) {
            chunk.getMetadata().put("total_chunks", total);
        }
        return result;
    }

    private List<String> recursiveSplit(String text, List<String> separators, int sepIdx, SplitterConfig config) {
        int chunkChars = config.getChunkSize() * CHARS_PER_TOKEN;

        if (text.length() <= chunkChars || sepIdx >= separators.size()) {
            return Collections.singletonList(text);
        }

        String separator = separators.get(sepIdx);
        List<String> parts = splitBySeparator(text, separator);

        if (parts.size() == 1) {
            return recursiveSplit(text, separators, sepIdx + 1, config);
        }

        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part.length() <= chunkChars) {
                result.add(part);
            } else {
                result.addAll(recursiveSplit(part, separators, sepIdx + 1, config));
            }
        }

        if (config.getOverlap() > 0 && result.size() > 1) {
            result = applyOverlap(result, config.getChunkSize() * CHARS_PER_TOKEN,
                    config.getOverlap() * CHARS_PER_TOKEN);
        }

        return result;
    }

    private List<String> splitBySeparator(String text, String separator) {
        if (separator.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (char c : text.toCharArray()) {
                result.add(String.valueOf(c));
            }
            return result;
        }

        String[] parts = text.split(separator, -1);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i < parts.length - 1) {
                part += separator;
            }
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    private List<String> applyOverlap(List<String> chunks, int maxChars, int overlapChars) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (i > 0 && overlapChars > 0) {
                String prev = chunks.get(i - 1);
                String tailOverlap = prev.substring(Math.max(prev.length() - overlapChars, 0));
                chunk = tailOverlap + chunk;
            }
            result.add(chunk);
        }
        return result;
    }

}
