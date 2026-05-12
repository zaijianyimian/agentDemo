package com.example.demo.service.listener;

/**
 * 某类内容监听器的连接测试能力。
 */
public interface ListenerConnectionTester<C, R> {

    R testConnection(C config);
}
