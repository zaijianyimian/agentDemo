package com.example.demo.chat.application;

import com.example.demo.chat.domain.ChatMessageEntity;
import com.example.demo.chat.domain.ChatSession;
import com.example.demo.chat.persistence.ChatMessageMapper;
import com.example.demo.chat.persistence.ChatSessionMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {

    private final ChatSessionMapper sessions = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final CurrentUserContext currentUser = mock(CurrentUserContext.class);
    private final ChatHistoryService service = new ChatHistoryService(sessions, messages, currentUser);

    @Test
    void knownForeignSessionIsNotFoundBeforeAnyMessageReadOrWrite() {
        when(currentUser.requireUserId()).thenReturn(7L);
        when(sessions.selectById(81L)).thenReturn(null);

        assertThatThrownBy(() -> service.getSessionMessages(81L))
                .isInstanceOf(UserResourceNotFoundException.class);
        assertThatThrownBy(() -> service.addMessage(81L, "user", "secret", null))
                .isInstanceOf(UserResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteSession(81L))
                .isInstanceOf(UserResourceNotFoundException.class);

        verifyNoInteractions(messages);
        verify(sessions, never()).updateById(org.mockito.ArgumentMatchers.any(ChatSession.class));
    }

    @Test
    void messageOwnerIsCopiedFromTheOwnedSession() {
        when(currentUser.requireUserId()).thenReturn(7L);
        ChatSession session = ChatSession.builder().id(11L).userId(7L).messageCount(0).build();
        when(sessions.selectById(11L)).thenReturn(session);

        service.addMessage(11L, "user", "hello", "worker");

        ArgumentCaptor<ChatMessageEntity> inserted = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(messages).insert(inserted.capture());
        assertThat(inserted.getValue().getUserId()).isEqualTo(7L);
        assertThat(inserted.getValue().getSessionId()).isEqualTo(11L);
    }
}
