package com.example.demo.chat.application;

import com.example.demo.chat.domain.ChatHistory;
import com.example.demo.chat.persistence.ChatHistoryMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryAccessServiceTest {

    @Test
    void foreignMessageIdIsNotFoundBeforeUpdate() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.requireUserId()).thenReturn(7L);
        when(mapper.selectById(91L)).thenReturn(null);
        ChatHistoryAccessService service = new ChatHistoryAccessService(mapper, currentUser);
        ChatHistory update = ChatHistory.builder().id(91L).content("overwrite").build();

        assertThatThrownBy(() -> service.update(update))
                .isInstanceOf(UserResourceNotFoundException.class);

        verify(mapper, never()).updateById(update);
    }
}
