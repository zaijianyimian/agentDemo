package com.example.demo.schedule.application;

import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserSessionSnapshot;
import com.example.demo.shared.context.UserSessionValidator;
import com.example.demo.shared.events.UserEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owner-scoped schedule SSE registry. */
@Slf4j
@Service
public class ScheduleNotificationService {

    private final UserSessionValidator sessions;
    private final CurrentUserContext currentUser;
    private final ObjectMapper objectMapper;
    private final Map<Long, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public ScheduleNotificationService(
            UserSessionValidator sessions,
            CurrentUserContext currentUser,
            ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    public Flux<ServerSentEvent<String>> subscribe() {
        return subscribe(sessions.captureAuthenticatedSession());
    }

    Flux<ServerSentEvent<String>> subscribe(UserSessionSnapshot session) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Subscription subscription = new Subscription(session, sink);
        subscriptions.computeIfAbsent(session.userId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(subscription);

        Flux<ServerSentEvent<String>> initial = Flux.just(ServerSentEvent.<String>builder()
                .event("connected").data("schedule-stream-ready").build());
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(20))
                .handle((ignored, output) -> {
                    if (sessions.isActive(session)) {
                        output.next(ServerSentEvent.<String>builder()
                                .event("ping").data("keep-alive").build());
                    } else {
                        remove(subscription);
                        sink.tryEmitComplete();
                        output.complete();
                    }
                });
        return initial.concatWith(sink.asFlux()).mergeWith(heartbeat)
                .doFinally(ignored -> remove(subscription));
    }

    public void publish(String eventName, ScheduleEvent event) {
        if (event == null || event.getId() == null || event.getUserId() == null
                || currentUser.requireUserId() != event.getUserId()) {
            throw new IllegalArgumentException("日程事件 owner 与执行上下文不匹配");
        }
        UserEventEnvelope<ScheduleEvent> envelope = new UserEventEnvelope<>(
                UUID.randomUUID().toString(), 1, event.getUserId(), LocalDateTime.now(),
                eventName, "schedule_event", event.getId().toString(), event);
        final String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("序列化日程 SSE 事件失败", error);
        }
        ServerSentEvent<String> message = ServerSentEvent.<String>builder()
                .event(eventName).data(json).build();
        for (Subscription subscription : Set.copyOf(
                subscriptions.getOrDefault(event.getUserId(), Set.of()))) {
            if (!sessions.isActive(subscription.session())) {
                remove(subscription);
                subscription.sink().tryEmitComplete();
                continue;
            }
            subscription.sink().tryEmitNext(message);
        }
    }

    private void remove(Subscription subscription) {
        Set<Subscription> ownerSubscriptions = subscriptions.get(subscription.session().userId());
        if (ownerSubscriptions == null) {
            return;
        }
        ownerSubscriptions.remove(subscription);
        if (ownerSubscriptions.isEmpty()) {
            subscriptions.remove(subscription.session().userId(), ownerSubscriptions);
        }
    }

    private record Subscription(
            UserSessionSnapshot session,
            Sinks.Many<ServerSentEvent<String>> sink) {
    }
}
