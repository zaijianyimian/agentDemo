package com.example.demo.service.email;

import com.example.demo.entity.EmailConfig;
import com.example.demo.service.listener.ContentListenerFactory;
import com.example.demo.service.listener.ListenerConnectionTester;
import com.example.demo.service.listener.ListenerContentType;
import com.example.demo.service.listener.ListenerRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 邮件监听工厂。
 */
@Component
@RequiredArgsConstructor
public class EmailContentListenerFactory implements ContentListenerFactory<EmailConfig, EmailConnectionTestService.EmailTestResult> {

    private final EmailListenerService emailListenerService;
    private final EmailConnectionTestService emailConnectionTestService;

    @Override
    public ListenerContentType contentType() {
        return ListenerContentType.EMAIL;
    }

    @Override
    public ListenerRuntime<EmailConfig> runtime() {
        return emailListenerService;
    }

    @Override
    public ListenerConnectionTester<EmailConfig, EmailConnectionTestService.EmailTestResult> tester() {
        return emailConnectionTestService;
    }
}
