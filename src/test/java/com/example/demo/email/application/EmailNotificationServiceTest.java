package com.example.demo.email.application;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.shared.context.UserSessionSnapshot;
import com.example.demo.shared.context.UserSessionValidator;
import com.example.demo.shared.context.CurrentUserContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTest {

    @Test
    void emailEventIsDeliveredOnlyToItsOwner() throws Exception {
        UserSessionValidator sessions = mock(UserSessionValidator.class);
        UserSessionSnapshot userA = new UserSessionSnapshot(7L, 3);
        UserSessionSnapshot userB = new UserSessionSnapshot(8L, 4);
        when(sessions.isActive(userA)).thenReturn(true);
        when(sessions.isActive(userB)).thenReturn(true);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        EmailNotificationService service = new EmailNotificationService(sessions, currentUser);
        SseEmitter emitterA = mock(SseEmitter.class);
        SseEmitter emitterB = mock(SseEmitter.class);
        service.subscribe(emitterA, userA);
        service.subscribe(emitterB, userB);
        clearInvocations(emitterA, emitterB);

        service.sendNewEmailNotification(EmailMessage.builder()
                .userId(7L)
                .messageId("m-7")
                .subject("A only")
                .build());

        verify(emitterA).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitterB, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void invalidatedTokenIsClosedBeforeHeartbeatOrEventDelivery() throws Exception {
        UserSessionValidator sessions = mock(UserSessionValidator.class);
        UserSessionSnapshot snapshot = new UserSessionSnapshot(7L, 3);
        when(sessions.isActive(snapshot)).thenReturn(false);
        EmailNotificationService service = new EmailNotificationService(sessions, mock(CurrentUserContext.class));
        SseEmitter emitter = mock(SseEmitter.class);
        service.subscribe(emitter, snapshot);
        clearInvocations(emitter);

        service.heartbeat();

        verify(emitter).complete();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }
}
