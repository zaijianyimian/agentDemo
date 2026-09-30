package com.example.demo.schedule.application;

import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.schedule.persistence.ScheduleEventMapper;
import com.example.demo.shared.context.CurrentUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 链路的日程创建幂等性。
 *
 * <p>覆盖三种必须只产生一条日程的场景：同一动作重复提交、并发重试、
 * 以及业务提交成功但响应丢失后的重放。同时确认不同动作不会被合并。</p>
 */
class ScheduleEventIdempotencyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 30, 15, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 9, 30, 16, 0);

    private ScheduleEventMapper mapper;
    private CurrentUserContext currentUser;
    private ScheduleEventService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ScheduleEventMapper.class);
        currentUser = mock(CurrentUserContext.class);
        service = new ScheduleEventService(mapper, currentUser);
        when(currentUser.requireUserId()).thenReturn(7L);
    }

    @Test
    @DisplayName("首次创建写入幂等键、来源与完整时间字段")
    void firstCreatePersistsIdempotencyAndSource() {
        when(mapper.selectOne(any())).thenReturn(null);

        ScheduleEvent created = service.createIdempotently(
                7L, "chat:s1:turn1:tc1", "面试", "准备一下", "会议室 A",
                START, END, "Asia/Shanghai",
                "3f6b0d1e-1c2a-4c3d-8e4f-5a6b7c8d9e0f", "EMAIL");

        ArgumentCaptor<ScheduleEvent> inserted = ArgumentCaptor.forClass(ScheduleEvent.class);
        verify(mapper).insert(inserted.capture());

        ScheduleEvent row = inserted.getValue();
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getIdempotencyKey()).isEqualTo("chat:s1:turn1:tc1");
        assertThat(row.getEventTime()).isEqualTo(START);
        assertThat(row.getEndTime()).isEqualTo(END);
        assertThat(row.getTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(row.getSourceType()).isEqualTo("EMAIL");
        assertThat(row.getSourceEmailEventId())
                .isEqualTo("3f6b0d1e-1c2a-4c3d-8e4f-5a6b7c8d9e0f");
        assertThat(row.getEventDate()).isEqualTo(START.toLocalDate());
        assertThat(row.getReminderEnabled()).isTrue();
        assertThat(created).isSameAs(row);
    }

    @Test
    @DisplayName("相同幂等键重复提交返回原始记录且不重复插入")
    void repeatedRequestReturnsOriginalResult() {
        ScheduleEvent existing = ScheduleEvent.builder()
                .id(501L)
                .userId(7L)
                .idempotencyKey("chat:s1:turn1:tc1")
                .title("面试")
                .eventTime(START)
                .endTime(END)
                .build();
        when(mapper.selectOne(any())).thenReturn(existing);

        ScheduleEvent result = service.createIdempotently(
                7L, "chat:s1:turn1:tc1", "面试", null, null,
                START, END, null, null, "CHAT");

        assertThat(result.getId()).isEqualTo(501L);
        verify(mapper, never()).insert(any(ScheduleEvent.class));
    }

    @Test
    @DisplayName("并发重试撞唯一键时回读胜出记录，不抛出给调用方")
    void concurrentRetryFallsBackToWinnerRecord() {
        ScheduleEvent winner = ScheduleEvent.builder()
                .id(502L)
                .userId(7L)
                .idempotencyKey("chat:s1:turn1:tc1")
                .build();
        // 首次查不到 -> 插入撞唯一键 -> 回读命中胜出记录。
        when(mapper.selectOne(any())).thenReturn(null, winner);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_schedule_event_user_idempotency"))
                .when(mapper).insert(any(ScheduleEvent.class));

        ScheduleEvent result = service.createIdempotently(
                7L, "chat:s1:turn1:tc1", "面试", null, null,
                START, END, null, null, "CHAT");

        assertThat(result.getId()).isEqualTo(502L);
        verify(mapper, times(1)).insert(any(ScheduleEvent.class));
    }

    @Test
    @DisplayName("不同动作使用不同幂等键，各自创建")
    void differentActionsCreateSeparateSchedules() {
        when(mapper.selectOne(any())).thenReturn(null);

        service.createIdempotently(7L, "chat:s1:turn1:tc1", "面试", null, null,
                START, END, null, null, "CHAT");
        service.createIdempotently(7L, "chat:s1:turn1:tc2", "复盘", null, null,
                START.plusHours(2), END.plusHours(2), null, null, "CHAT");

        ArgumentCaptor<ScheduleEvent> inserted = ArgumentCaptor.forClass(ScheduleEvent.class);
        verify(mapper, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues())
                .extracting(ScheduleEvent::getIdempotencyKey)
                .containsExactly("chat:s1:turn1:tc1", "chat:s1:turn1:tc2");
        assertThat(inserted.getAllValues())
                .extracting(ScheduleEvent::getTitle)
                .containsExactly("面试", "复盘");
    }

    @Test
    @DisplayName("结束时间不晚于开始时间时拒绝创建")
    void rejectsInvalidInterval() {
        assertThatThrownBy(() -> service.createIdempotently(
                7L, "chat:s1:turn1:tc1", "面试", null, null,
                END, START, null, null, "CHAT"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.createIdempotently(
                7L, "chat:s1:turn1:tc1", "面试", null, null,
                START, START, null, null, "CHAT"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mapper, never()).insert(any(ScheduleEvent.class));
    }

    @Test
    @DisplayName("编辑日程不会抹掉 AI 链路的幂等键与来源")
    void updatePreservesServerOwnedProvenance() {
        ScheduleEvent existing = ScheduleEvent.builder()
                .id(600L)
                .userId(7L)
                .idempotencyKey("email:event-1:7:tc9")
                .sourceType("EMAIL")
                .sourceEmailEventId("3f6b0d1e-1c2a-4c3d-8e4f-5a6b7c8d9e0f")
                .sourceEmailId(88L)
                .endTime(END)
                .timezone("Asia/Shanghai")
                .build();
        when(mapper.selectById(600L)).thenReturn(existing);

        // 前端编辑只提交标题等业务字段，不含幂等键与来源。
        ScheduleEvent edit = ScheduleEvent.builder()
                .id(600L)
                .title("面试（改期）")
                .eventTime(START)
                .build();

        service.update(edit);

        ArgumentCaptor<ScheduleEvent> saved = ArgumentCaptor.forClass(ScheduleEvent.class);
        verify(mapper).updateById(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("email:event-1:7:tc9");
        assertThat(saved.getValue().getSourceType()).isEqualTo("EMAIL");
        assertThat(saved.getValue().getSourceEmailEventId())
                .isEqualTo("3f6b0d1e-1c2a-4c3d-8e4f-5a6b7c8d9e0f");
        // 缺省的结束时间与时区保留原值，不被清空。
        assertThat(saved.getValue().getEndTime()).isEqualTo(END);
        assertThat(saved.getValue().getTimezone()).isEqualTo("Asia/Shanghai");
    }
}
