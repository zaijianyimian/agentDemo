package com.example.demo.schedule.application;

import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserSessionSnapshot;
import com.example.demo.shared.context.UserSessionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleNotificationServiceTest {

    @Test
    void scheduleEventIsDeliveredOnlyToItsOwnerAndUsesEnvelope() {
        UserSessionValidator sessions = mock(UserSessionValidator.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        UserSessionSnapshot userA = new UserSessionSnapshot(7L, 1);
        UserSessionSnapshot userB = new UserSessionSnapshot(8L, 1);
        when(sessions.isActive(userA)).thenReturn(true);
        when(sessions.isActive(userB)).thenReturn(true);
        when(currentUser.requireUserId()).thenReturn(7L);
        ScheduleNotificationService service = new ScheduleNotificationService(
                sessions, currentUser, new ObjectMapper().findAndRegisterModules());
        List<ServerSentEvent<String>> eventsA = new CopyOnWriteArrayList<>();
        List<ServerSentEvent<String>> eventsB = new CopyOnWriteArrayList<>();
        var subscriptionA = service.subscribe(userA).subscribe(eventsA::add);
        var subscriptionB = service.subscribe(userB).subscribe(eventsB::add);

        service.publish("created", ScheduleEvent.builder().id(11L).userId(7L).title("A").build());

        assertThat(eventsA).hasSize(2);
        assertThat(eventsA.get(1).data()).contains("\"user_id\":7", "\"resource_id\":\"11\"");
        assertThat(eventsB).hasSize(1);
        subscriptionA.dispose();
        subscriptionB.dispose();
    }

    @Test
    void ownerConflictIsRejectedBeforeEmission() {
        UserSessionValidator sessions = mock(UserSessionValidator.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.requireUserId()).thenReturn(7L);
        ScheduleNotificationService service = new ScheduleNotificationService(
                sessions, currentUser, new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> service.publish(
                "updated", ScheduleEvent.builder().id(12L).userId(8L).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
