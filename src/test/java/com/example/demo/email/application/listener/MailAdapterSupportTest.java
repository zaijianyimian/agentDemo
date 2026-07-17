package com.example.demo.email.application.listener;

import com.example.demo.email.domain.listener.ListenMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class MailAdapterSupportTest {

    @Test
    void apiAdaptersSupportWebhookAndDeltaModes() {
        OAuthAccessTokenService tokenService = null;
        ObjectMapper objectMapper = new ObjectMapper();
        WebClient.Builder builder = WebClient.builder();
        GmailApiMailAdapter gmail = new GmailApiMailAdapter(tokenService, objectMapper, builder);
        MicrosoftGraphMailAdapter graph = new MicrosoftGraphMailAdapter(tokenService, objectMapper, builder);

        assertThat(gmail.supportsListenMode(ListenMode.WEBHOOK)).isTrue();
        assertThat(gmail.supportsListenMode(ListenMode.DELTA_SYNC)).isTrue();
        assertThat(graph.supportsListenMode(ListenMode.WEBHOOK)).isTrue();
        assertThat(graph.supportsListenMode(ListenMode.DELTA_SYNC)).isTrue();
    }
}
