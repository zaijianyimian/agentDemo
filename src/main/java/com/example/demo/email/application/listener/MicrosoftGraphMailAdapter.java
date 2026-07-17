package com.example.demo.email.application.listener;

import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailMessageKey;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import com.example.demo.email.domain.listener.ProviderSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Microsoft Graph 邮件适配器。
 * 通过 Graph 的 {@code /me/mailFolders/.../messages/delta} 接口按 deltaLink 增量拉取邮件，并支持 subscription 订阅。
 */
@Component
public class MicrosoftGraphMailAdapter implements MailSourceAdapter {

    private static final String BASE_URL = "https://graph.microsoft.com/v1.0";

    private final OAuthAccessTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public MicrosoftGraphMailAdapter(OAuthAccessTokenService tokenService, ObjectMapper objectMapper, WebClient.Builder builder) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.webClient = builder.build();
    }

    @Override
    public MailProvider provider() {
        return MailProvider.MICROSOFT_GRAPH;
    }

    @Override
    public boolean supportsListenMode(ListenMode mode) {
        return mode == ListenMode.WEBHOOK || mode == ListenMode.DELTA_SYNC || mode == ListenMode.POLLING;
    }

    @Override
    public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
        String token = tokenService.resolveAccessToken(config, provider());
        ProviderSettings settings = ProviderSettings.fromConfig(config, objectMapper);
        String folder = settings.stringOrDefault("folderId", "inbox");
        String uri = cursor != null && StringUtils.hasText(cursor.value())
                ? cursor.value()
                : BASE_URL + "/me/mailFolders/" + folder + "/messages/delta?$top=10";
        Map<String, Object> response = graphGet(token, uri);
        String nextCursor = firstText(asString(response.get("@odata.deltaLink")), asString(response.get("@odata.nextLink")));
        List<MailboxMessage> result = new ArrayList<>();
        Object value = response.get("value");
        if (value instanceof List<?> messages) {
            for (Object item : messages) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> message = toStringObjectMap(raw);
                if (message.containsKey("@removed")) {
                    continue;
                }
                EmailMessage emailMessage = toEmailMessage(config, message);
                result.add(new MailboxMessage(
                        MailMessageKey.of(config, provider(), "graph:" + asString(message.get("id"))),
                        emailMessage,
                        MailCursor.of("GRAPH_DELTA_LINK", nextCursor),
                        message
                ));
            }
        }
        return result;
    }

    @Override
    public List<MailboxMessage> fetchFromNotification(EmailConfig config, EmailListenerState state, Map<String, Object> notification) {
        return fetchNewMessages(config, new MailCursor(state.getCursorType(), state.getCursorValue()));
    }

    @Override
    public SubscriptionRegistration registerOrRenewSubscription(EmailConfig config, EmailListenerState state) {
        ProviderSettings settings = ProviderSettings.fromConfig(config, objectMapper);
        String notificationUrl = settings.string("notificationUrl");
        if (!StringUtils.hasText(notificationUrl)) {
            return null;
        }
        String token = tokenService.resolveAccessToken(config, provider());
        String folder = settings.stringOrDefault("folderId", "inbox");
        String resource = "/me/mailFolders/" + folder + "/messages";
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(48);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("changeType", settings.stringOrDefault("changeType", "created"));
        body.put("notificationUrl", notificationUrl);
        body.put("resource", resource);
        body.put("expirationDateTime", expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        String clientState = settings.string("clientState");
        if (StringUtils.hasText(clientState)) {
            body.put("clientState", clientState);
        }
        Map<String, Object> response = graphPost(token, BASE_URL + "/subscriptions", body);
        String expiration = asString(response.get("expirationDateTime"));
        if (StringUtils.hasText(expiration)) {
            expiresAt = OffsetDateTime.parse(expiration).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
        return new SubscriptionRegistration(asString(response.get("id")), expiresAt, resource, state.getCursorType(), state.getCursorValue());
    }

    private Map<String, Object> graphGet(String token, String uri) {
        String body = webClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return readMap(body);
    }

    private Map<String, Object> graphPost(String token, String uri, Map<String, Object> payload) {
        String body = webClient.post()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return readMap(body);
    }

    private EmailMessage toEmailMessage(EmailConfig config, Map<String, Object> raw) {
        Map<?, ?> from = asMap(raw.get("from"));
        Map<?, ?> emailAddress = from == null ? null : asMap(from.get("emailAddress"));
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .accountEmail(config.getEmail())
                .messageId(firstText(asString(raw.get("internetMessageId")), asString(raw.get("id"))))
                .from(emailAddress == null ? null : asString(emailAddress.get("address")))
                .fromName(emailAddress == null ? null : asString(emailAddress.get("name")))
                .to(addresses(raw.get("toRecipients")))
                .cc(addresses(raw.get("ccRecipients")))
                .subject(asString(raw.get("subject")))
                .seen(Boolean.TRUE.equals(raw.get("isRead")));
        Map<?, ?> body = asMap(raw.get("body"));
        if (body != null) {
            String contentType = asString(body.get("contentType"));
            String content = asString(body.get("content"));
            if ("html".equalsIgnoreCase(contentType)) {
                builder.htmlContent(content);
            } else {
                builder.textContent(content);
            }
        }
        String received = asString(raw.get("receivedDateTime"));
        if (StringUtils.hasText(received)) {
            builder.receivedDate(OffsetDateTime.parse(received).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        }
        String sent = asString(raw.get("sentDateTime"));
        if (StringUtils.hasText(sent)) {
            builder.sentDate(OffsetDateTime.parse(sent).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        }
        return builder.build();
    }

    private List<String> addresses(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            Map<?, ?> recipient = asMap(item);
            Map<?, ?> emailAddress = recipient == null ? null : asMap(recipient.get("emailAddress"));
            if (emailAddress != null) {
                values.add(asString(emailAddress.get("address")));
            }
        }
        return values;
    }

    private Map<String, Object> readMap(String body) {
        try {
            return objectMapper.readValue(body == null ? "{}" : body, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("解析 Microsoft Graph 响应失败", e);
        }
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
