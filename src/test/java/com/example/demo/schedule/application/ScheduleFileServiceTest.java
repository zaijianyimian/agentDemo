package com.example.demo.schedule.application;

import com.example.demo.infrastructure.storage.OwnedStorageResolver;
import com.example.demo.schedule.domain.ScheduleEvent;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduleFileServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sameDateProducesIndependentFilesForTwoUsers() {
        AtomicLong owner = new AtomicLong(101L);
        CurrentUserContext current = () -> Optional.of(new UserContext(owner.get()));
        OwnedStorageResolver storage = new OwnedStorageResolver(
                current, temporaryDirectory.resolve("users").toString());
        ScheduleFileService service = new ScheduleFileService(storage, current);
        LocalDate date = LocalDate.of(2026, 9, 13);

        service.saveScheduleByDate(date, List.of(ScheduleEvent.builder().title("A").eventDate(date).build()));
        owner.set(202L);
        service.saveScheduleByDate(date, List.of(ScheduleEvent.builder().title("B").eventDate(date).build()));

        owner.set(101L);
        assertThat(service.readScheduleFile(date)).contains("A").doesNotContain("B");
        owner.set(202L);
        assertThat(service.readScheduleFile(date)).contains("B").doesNotContain("A");
        assertThrows(IllegalArgumentException.class,
                () -> service.readScheduleFileByPath("/tmp/foreign-schedule.md"));
    }
}
