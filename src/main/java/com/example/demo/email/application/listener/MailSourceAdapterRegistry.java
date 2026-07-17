package com.example.demo.email.application.listener;

import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.listener.MailProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 邮箱 provider 适配器注册中心。
 * 收集所有 {@link MailSourceAdapter} Bean，按 {@link MailProvider} 建立枚举映射。
 */
@Component
public class MailSourceAdapterRegistry {

    private final Map<MailProvider, MailSourceAdapter> adapters = new EnumMap<>(MailProvider.class);

    public MailSourceAdapterRegistry(List<MailSourceAdapter> adapters) {
        for (MailSourceAdapter adapter : adapters) {
            this.adapters.put(adapter.provider(), adapter);
        }
    }

    /**
     * 根据 {@link MailProvider} 获取对应 provider 适配器；未注册时抛异常。
     */
    public MailSourceAdapter get(MailProvider provider) {
        MailSourceAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("未注册邮箱提供商适配器: " + provider);
        }
        return adapter;
    }
}
