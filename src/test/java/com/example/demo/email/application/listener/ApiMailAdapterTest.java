package com.example.demo.email.application.listener;

import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailboxMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiMailAdapterTest {

    @Test
    void gmailHistoryFetchNormalizesMessageAndAdvancesHistoryCursor() {
        OAuthAccessTokenService tokenService = mock(OAuthAccessTokenService.class);
        EmailConfig config = EmailConfig.builder().id(1L).email("me@gmail.com").build();
        when(tokenService.resolveAccessToken(config, com.example.demo.email.domain.listener.MailProvider.GMAIL_API))
                .thenReturn("token");
        ExchangeFunction exchange = request -> {
            String uri = request.url().toString();
            if (uri.contains("/history?")) {
                return json("""
                        {"historyId":"101","history":[{"messagesAdded":[{"message":{"id":"msg-1"}}]}]}
                        """);
            }
            return json("""
                    {
                      "id":"msg-1",
                      "historyId":"102",
                      "internalDate":"1710000000000",
                      "labelIds":["INBOX"],
                      "payload":{
                        "headers":[
                          {"name":"From","value":"sender@example.com"},
                          {"name":"To","value":"me@gmail.com"},
                          {"name":"Subject","value":"Gmail Subject"},
                          {"name":"Message-ID","value":"<g1@example.com>"}
                        ],
                        "parts":[{"mimeType":"text/plain","body":{"data":"SGVsbG8"}}]
                      }
                    }
                    """);
        };
        GmailApiMailAdapter adapter = new GmailApiMailAdapter(tokenService, new ObjectMapper(), WebClient.builder().exchangeFunction(exchange));

        List<MailboxMessage> messages = adapter.fetchNewMessages(config, MailCursor.of("GMAIL_HISTORY_ID", "100"));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).emailMessage().getSubject()).isEqualTo("Gmail Subject");
        assertThat(messages.get(0).emailMessage().getTextContent()).isEqualTo("Hello");
        assertThat(messages.get(0).cursorAfter().value()).isEqualTo("102");
    }

    @Test
    void graphDeltaFetchNormalizesMessageAndAdvancesDeltaCursor() {
        OAuthAccessTokenService tokenService = mock(OAuthAccessTokenService.class);
        EmailConfig config = EmailConfig.builder().id(1L).email("me@outlook.com").build();
        when(tokenService.resolveAccessToken(config, com.example.demo.email.domain.listener.MailProvider.MICROSOFT_GRAPH))
                .thenReturn("token");
        ExchangeFunction exchange = request -> json("""
                {
                  "@odata.deltaLink":"https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?$deltatoken=abc",
                  "value":[{
                    "id":"m1",
                    "internetMessageId":"<m1@example.com>",
                    "subject":"Graph Subject",
                    "from":{"emailAddress":{"address":"sender@example.com","name":"Sender"}},
                    "toRecipients":[{"emailAddress":{"address":"me@outlook.com"}}],
                    "body":{"contentType":"text","content":"Graph body"},
                    "isRead":false,
                    "receivedDateTime":"2026-06-28T08:00:00Z"
                  }]
                }
                """);
        MicrosoftGraphMailAdapter adapter = new MicrosoftGraphMailAdapter(tokenService, new ObjectMapper(), WebClient.builder().exchangeFunction(exchange));

        List<MailboxMessage> messages = adapter.fetchNewMessages(config, MailCursor.empty());

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).emailMessage().getSubject()).isEqualTo("Graph Subject");
        assertThat(messages.get(0).emailMessage().getTextContent()).isEqualTo("Graph body");
        assertThat(messages.get(0).cursorAfter().value()).contains("deltatoken=abc");
    }

    private Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .build());
    }
}
