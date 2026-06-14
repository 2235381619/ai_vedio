package cn.bugstack.ai.domain.agent.service.rag.chunk;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TextSplitterContext {

    private final Map<String, TextSplitterStrategy> strategyMap;

    public TextSplitterContext(List<TextSplitterStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        TextSplitterStrategy::getStrategyName,
                        s -> s,
                        (existing, replacement) -> {
                            throw new IllegalArgumentException(
                                    "Duplicate splitter strategy name: " + existing.getStrategyName());
                        }
                ));
    }

    public List<Document> split(String strategyName, List<Document> documents) {
        TextSplitterStrategy strategy = strategyMap.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown splitter strategy: '" + strategyName + "'. Available: " + strategyMap.keySet());
        }
        return strategy.apply(documents);
    }

    public List<String> listStrategies() {
        return List.copyOf(strategyMap.keySet());
    }

    public TextSplitterStrategy getStrategy(String strategyName) {
        return strategyMap.get(strategyName);
    }

}
