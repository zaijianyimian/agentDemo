package com.example.demo.email.application;

import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailProvider;
import org.springframework.stereotype.Component;

/**
 * 邮箱配置默认值应用器。
 * 根据 provider / listenMode 自动补充协议、端口、降级模式等缺省值。
 */
@Component
public class EmailListenerConfigSupport {

    /**
     * 按 provider 推导协议、按 listenMode 设置默认的降级模式等缺省值；已显式设置的字段不会被覆盖。
     */
    public void applyDefaults(EmailConfig config) {
        if (config == null) {
            return;
        }
        MailProvider provider = MailProvider.fromConfig(config);
        ListenMode listenMode = ListenMode.fromConfig(config);
        config.setProvider(provider.name());
        config.setListenMode(listenMode.name());
        if (provider == MailProvider.GENERIC_IMAP && config.getProtocol() == null) {
            config.setProtocol("imap");
        }
        if (provider == MailProvider.GENERIC_POP3 && config.getProtocol() == null) {
            config.setProtocol("pop3");
        }
        if (listenMode == ListenMode.IMAP_IDLE && config.getFallbackListenMode() == null) {
            config.setFallbackListenMode(ListenMode.POLLING.name());
        }
    }
}
