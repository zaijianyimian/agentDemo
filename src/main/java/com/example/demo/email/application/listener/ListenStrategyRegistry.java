package com.example.demo.email.application.listener;

import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.domain.listener.ListenMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 监听策略注册中心。
 * 收集所有 {@link ListenStrategy} Bean，按 {@link ListenMode} 建立枚举映射供 {@link EmailListenerManager} 取用。
 */
@Component
public class ListenStrategyRegistry {

    private final Map<ListenMode, ListenStrategy> strategies = new EnumMap<>(ListenMode.class);

    public ListenStrategyRegistry(List<ListenStrategy> strategies) {
        for (ListenStrategy strategy : strategies) {
            this.strategies.put(strategy.mode(), strategy);
        }
    }

    /**
     * 根据 {@link ListenMode} 获取对应监听策略；未注册时抛异常。
     */
    public ListenStrategy get(ListenMode mode) {
        ListenStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("未注册邮箱监听策略: " + mode);
        }
        return strategy;
    }
}
