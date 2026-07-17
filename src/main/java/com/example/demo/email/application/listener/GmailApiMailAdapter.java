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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gmail API 适配器。
 * 通过 Gmail REST API 的 {@code users.history} 接口按 historyId 增量拉取新邮件，并支持 watch 订阅。
 */
@Component
public class GmailApiMailAdapter implements MailSourceAdapter {

    private static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final OAuthAccessTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public GmailApiMailAdapter(OAuthAccessTokenService tokenService, ObjectMapper objectMapper, WebClient.Builder builder) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.webClient = builder.build();
    }

    @Override
    public MailProvider provider() {
        return MailProvider.GMAIL_API;
    }

    @Override
    public boolean supportsListenMode(ListenMode mode) {
        return mode == ListenMode.WEBHOOK || mode == ListenMode.DELTA_SYNC || mode == ListenMode.POLLING;
    }

    @Override
    public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
        String token = tokenService.resolveAccessToken(config, provider());
        ProviderSettings settings = ProviderSettings.fromConfig(config, objectMapper);
        String labelId = settings.stringOrDefault("labelId", "INBOX");
        String historyId = cursor == null ? null : cursor.value();
        Map<String, Object> response = StringUtils.hasText(historyId)
                ? gmailGet(token, BASE_URL + "/history?startHistoryId=" + historyId + "&historyTypes=messageAdded&labelId=" + labelId)
                : gmailGet(token, BASE_URL + "/messages?labelIds=" + labelId + "&maxResults=10&q=newer_than:1d");

        List<String> messageIds = StringUtils.hasText(historyId)
                ? messageIdsFromHistory(response)
                : messageIdsFromList(response);
        String latestHistoryId = asString(response.get("historyId"));
        List<MailboxMessage> result = new ArrayList<>();
        for (String messageId : messageIds) {
            Map<String, Object> raw = gmailGet(token, BASE_URL + "/messages/" + messageId + "?format=full");
            EmailMessage emailMessage = toEmailMessage(config, raw);
            String messageHistoryId = asString(raw.get("historyId"));
            if (StringUtils.hasText(messageHistoryId)) {
                latestHistoryId = messageHistoryId;
            }
            result.add(new MailboxMessage(
                    MailMessageKey.of(config, provider(), "gmail:" + messageId),
                    emailMessage,
                    MailCursor.of("GMAIL_HISTORY_ID", latestHistoryId),
                    raw
            ));
        }
        if (result.isEmpty() && StringUtils.hasText(latestHistoryId)) {
            result.add(new MailboxMessage(
                    MailMessageKey.of(config, provider(), "gmail-cursor:" + latestHistoryId),
                    null,
                    MailCursor.of("GMAIL_HISTORY_ID", latestHistoryId),
                    null
            ));
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
        String topicName = settings.string("topicName");
        if (!StringUtils.hasText(topicName)) {
            return null;
        }
        String token = tokenService.resolveAccessToken(config, provider());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topicName", topicName);
        String labelId = settings.stringOrDefault("labelId", "INBOX");
        body.put("labelIds", List.of(labelId));
        body.put("labelFilterBehavior", "include");
        Map<String, Object> response = gmailPost(token, BASE_URL + "/watch", body);
        String historyId = asString(response.get("historyId"));
        LocalDateTime expiration = null;
        String rawExpiration = asString(response.get("expiration"));
        if (StringUtils.hasText(rawExpiration)) {
            expiration = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(rawExpiration)), ZoneId.systemDefault());
        }
        return new SubscriptionRegistration(topicName, expiration, labelId, "GMAIL_HISTORY_ID", historyId);
    }

    private Map<String, Object> gmailGet(String token, String uri) {
        String body = webClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return readMap(body);
    }

    private Map<String, Object> gmailPost(String token, String uri, Map<String, Object> payload) {
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

    private List<String> messageIdsFromList(Map<String, Object> response) {
        List<String> ids = new ArrayList<>();
        Object messages = response.get("messages");
        if (messages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String id = asString(map.get("id"));
                    if (StringUtils.hasText(id)) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private List<String> messageIdsFromHistory(Map<String, Object> response) {
        List<String> ids = new ArrayList<>();
        Object history = response.get("history");
        if (history instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> historyItem)) {
                    continue;
                }
                Object messagesAdded = historyItem.get("messagesAdded");
                if (messagesAdded instanceof List<?> addedList) {
                    for (Object added : addedList) {
                        if (added instanceof Map<?, ?> addedMap && addedMap.get("message") instanceof Map<?, ?> message) {
                            String id = asString(message.get("id"));
                            if (StringUtils.hasText(id)) {
                                ids.add(id);
                            }
                        }
                    }
                }
            }
        }
        return ids;
    }

    private EmailMessage toEmailMessage(EmailConfig config, Map<String, Object> raw) {
        Map<String, String> headers = headers(raw);
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .accountEmail(config.getEmail())
                .messageId(firstText(headers.get("Message-ID"), asString(raw.get("id"))))
                .from(headers.get("From"))
                .fromName(headers.get("From"))
                .to(splitHeader(headers.get("To")))
                .cc(splitHeader(headers.get("Cc")))
                .subject(headers.get("Subject"))
                .textContent(body(raw, "text/plain"))
                .htmlContent(body(raw, "text/html"))
                .seen(labels(raw).contains("UNREAD") ? false : true);
        String internalDate = asString(raw.get("internalDate"));
        if (StringUtils.hasText(internalDate)) {
            builder.receivedDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(internalDate)), ZoneId.systemDefault()));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> headers(Map<String, Object> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        Object payload = raw.get("payload");
        if (!(payload instanceof Map<?, ?> payloadMap) || !(payloadMap.get("headers") instanceof List<?> headers)) {
            return result;
        }
        for (Object item : headers) {
            if (item instanceof Map<?, ?> header) {
                result.put(asString(header.get("name")), asString(header.get("value")));
            }
        }
        return result;
    }

    private String body(Map<String, Object> raw, String mimeType) {
        Object payload = raw.get("payload");
        if (payload instanceof Map<?, ?> payloadMap) {
            return findBody(payloadMap, mimeType);
        }
        return null;
    }

    private String findBody(Map<?, ?> part, String mimeType) {
        if (mimeType.equals(asString(part.get("mimeType"))) && part.get("body") instanceof Map<?, ?> body) {
            String data = asString(body.get("data"));
            if (StringUtils.hasText(data)) {
                return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
            }
        }
        Object parts = part.get("parts");
        if (parts instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> child) {
                    String value = findBody(child, mimeType);
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private List<String> labels(Map<String, Object> raw) {
        Object labelIds = raw.get("labelIds");
        if (!(labelIds instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private List<String> splitHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return List.of(value.split("\\s*,\\s*"));
    }

    private Map<String, Object> readMap(String body) {
        try {
            return objectMapper.readValue(body == null ? "{}" : body, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("解析 Gmail API 响应失败", e);
        }
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
