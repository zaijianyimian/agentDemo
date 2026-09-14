package com.example.demo.task.application;

import com.example.demo.email.application.EmailConfigService;
import com.example.demo.email.application.EmailSenderService;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import com.example.demo.task.domain.ScheduledTask;
import com.example.demo.task.persistence.JobLogMapper;
import com.example.demo.task.persistence.ScheduledTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledTaskServiceTest {

    private final ScheduledTaskMapper tasks = mock(ScheduledTaskMapper.class);
    private final JobLogMapper logs = mock(JobLogMapper.class);
    private final CurrentUserContext currentUser = mock(CurrentUserContext.class);
    private final ScheduledTaskService service = new ScheduledTaskService(
            tasks,
            logs,
            mock(EmailSenderService.class),
            mock(EmailConfigService.class),
            currentUser);

    @Test
    void createIgnoresClientOwnerAndUsesCurrentUser() {
        when(currentUser.requireUserId()).thenReturn(7L);
        ScheduledTask input = ScheduledTask.builder()
                .userId(81L)
                .name("mine")
                .taskType("REMINDER")
                .cronExpression("0 0 9 * * ?")
                .build();

        service.createTask(input);

        ArgumentCaptor<ScheduledTask> inserted = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(tasks).insert(inserted.capture());
        assertThat(inserted.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void foreignTaskIdIsNotFoundBeforeLogReadOrDelete() {
        when(tasks.selectById(81L)).thenReturn(null);

        assertThatThrownBy(() -> service.recentLogs(81L, 10))
                .isInstanceOf(UserResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteTask(81L))
                .isInstanceOf(UserResourceNotFoundException.class);

        verify(logs, never()).selectRecentByJobId(81L, 10);
        verify(logs, never()).deleteByJobId(81L);
    }
}
