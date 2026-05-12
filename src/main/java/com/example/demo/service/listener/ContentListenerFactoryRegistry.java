package com.example.demo.service.listener;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 内容监听工厂注册表。
 */
@Service
public class ContentListenerFactoryRegistry {

    private final Map<ListenerContentType, ContentListenerFactory<?, ?>> factories = new EnumMap<>(ListenerContentType.class);

    public ContentListenerFactoryRegistry(List<ContentListenerFactory<?, ?>> factoryList) {
        for (ContentListenerFactory<?, ?> factory : factoryList) {
            factories.put(factory.contentType(), factory);
        }
    }

    @SuppressWarnings("unchecked")
    public <C, T> ContentListenerFactory<C, T> get(ListenerContentType contentType) {
        ContentListenerFactory<?, ?> factory = factories.get(contentType);
        if (factory == null) {
            throw new IllegalArgumentException("未注册监听工厂: " + contentType);
        }
        return (ContentListenerFactory<C, T>) factory;
    }
}
