package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerConfigSupport;
import com.example.demo.email.domain.EmailConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailListenerConfigSupportTest {

    private final EmailListenerConfigSupport support = new EmailListenerConfigSupport();

    @Test
    void mapsLegacyImapConfigToGenericImapPolling() {
        EmailConfig config = EmailConfig.builder()
                .protocol("imap")
                .build();

        support.applyDefaults(config);

        assertThat(config.getProvider()).isEqualTo("GENERIC_IMAP");
        assertThat(config.getListenMode()).isEqualTo("POLLING");
    }

    @Test
    void mapsLegacyPop3ConfigToGenericPop3Polling() {
        EmailConfig config = EmailConfig.builder()
                .protocol("pop3")
                .build();

        support.applyDefaults(config);

        assertThat(config.getProvider()).isEqualTo("GENERIC_POP3");
        assertThat(config.getListenMode()).isEqualTo("POLLING");
    }

    @Test
    void addsPollingFallbackForImapIdle() {
        EmailConfig config = EmailConfig.builder()
                .provider("GENERIC_IMAP")
                .listenMode("IMAP_IDLE")
                .build();

        support.applyDefaults(config);

        assertThat(config.getFallbackListenMode()).isEqualTo("POLLING");
    }
}
