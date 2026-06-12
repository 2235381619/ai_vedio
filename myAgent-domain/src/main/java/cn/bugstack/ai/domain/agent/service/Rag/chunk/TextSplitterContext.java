package cn.bugstack.ai.domain.agent.service.Rag.chunk;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 切片策略上下文
 * <p>
 * 通过 Spring 自动注入所有 TextSplitterStrategy 实现，
 * 根据策略名称路由到对应的策略执行切片。
 */
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

    /**
     * 根据策略名执行切片
     */
    public List<Document> split(String strategyName, List<Document> documents) {
        TextSplitterStrategy strategy = strategyMap.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown splitter strategy: '" + strategyName + "'. Available: " + strategyMap.keySet());
        }
        return strategy.apply(documents);
    }

    /**
     * 获取所有可用策略名称
     */
    public List<String> listStrategies() {
        return List.copyOf(strategyMap.keySet());
    }

    /**
     * 获取指定策略实例
     */
    public TextSplitterStrategy getStrategy(String strategyName) {
        return strategyMap.get(strategyName);
    }

}
