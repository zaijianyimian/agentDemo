package com.example.demo.service.listener;

/**
 * 内容监听抽象工厂。
 *
 * 每一种内容源负责提供自己的运行时监听器和连接测试器。
 */
public interface ContentListenerFactory<C, T> {

    ListenerContentType contentType();

    ListenerRuntime<C> runtime();

    ListenerConnectionTester<C, T> tester();
}
