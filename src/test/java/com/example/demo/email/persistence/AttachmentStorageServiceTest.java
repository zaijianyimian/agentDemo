package com.example.demo.email.persistence;

import com.example.demo.infrastructure.storage.OwnedStorageResolver;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserContext;
import jakarta.mail.BodyPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentStorageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void attachmentKeyAndBytesAreOwnedByCurrentUser() throws Exception {
        AtomicLong owner = new AtomicLong(101L);
        CurrentUserContext current = () -> Optional.of(new UserContext(owner.get()));
        OwnedStorageResolver storage = new OwnedStorageResolver(
                current, temporaryDirectory.resolve("users").toString());
        AttachmentStorageService service = new AttachmentStorageService(storage, current);
        BodyPart part = mock(BodyPart.class);
        when(part.getFileName()).thenReturn("report.txt");
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream("A".getBytes()));
        when(part.getContentType()).thenReturn("text/plain");

        var attachment = service.save(part, "message-1", "a@example.test");

        assertThat(attachment.getUserId()).isEqualTo(101L);
        assertThat(attachment.getStorageKey()).isEqualTo("message-1/report.txt");
        Path stored = storage.resolveExisting(
                101L, OwnedStorageResolver.Category.EMAIL_ATTACHMENTS, attachment.getStorageKey());
        assertThat(Files.readString(stored)).isEqualTo("A");
        assertThat(stored).startsWith(temporaryDirectory.resolve("users/101/email-attachments"));
    }

    @Test
    void sameAttachmentKeyReadsOnlyTheCurrentOwnersBytes() throws Exception {
        AtomicLong owner = new AtomicLong(101L);
        CurrentUserContext current = () -> Optional.of(new UserContext(owner.get()));
        OwnedStorageResolver storage = new OwnedStorageResolver(
                current, temporaryDirectory.resolve("users").toString());
        AttachmentStorageService service = new AttachmentStorageService(storage, current);

        var attachmentA = service.save(part("A"), "same-message", "a@example.test");
        owner.set(202L);
        var attachmentB = service.save(part("B"), "same-message", "b@example.test");

        assertThat(service.read(attachmentB.getStorageKey())).isEqualTo("B".getBytes());
        owner.set(101L);
        assertThat(service.read(attachmentA.getStorageKey())).isEqualTo("A".getBytes());
    }

    private BodyPart part(String content) throws Exception {
        BodyPart part = mock(BodyPart.class);
        when(part.getFileName()).thenReturn("report.txt");
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        when(part.getContentType()).thenReturn("text/plain");
        return part;
    }
}
