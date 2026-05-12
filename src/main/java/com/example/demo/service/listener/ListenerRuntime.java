package com.example.demo.service.listener;

import java.util.Map;

/**
 * 某类内容监听器的运行时能力。
 */
public interface ListenerRuntime<C> {

    void start(C config);

    void stop(Long configId);

    void stopAll();

    Map<Long, Map<String, Object>> status();
}
