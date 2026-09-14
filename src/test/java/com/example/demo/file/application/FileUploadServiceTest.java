package com.example.demo.file.application;

import com.example.demo.file.domain.Document;
import com.example.demo.file.persistence.DocumentMapper;
import com.example.demo.infrastructure.storage.OwnedStorageResolver;
import com.example.demo.shared.application.FileContentExtractor;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserContext;
import com.example.demo.system.application.SystemSettingsService;
import com.example.demo.shared.web.UserResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void uploadReadAndDeleteRemainInsideEachOwnerDirectory() throws Exception {
        AtomicLong owner = new AtomicLong(101L);
        CurrentUserContext current = () -> Optional.of(new UserContext(owner.get()));
        OwnedStorageResolver storage = new OwnedStorageResolver(
                current, temporaryDirectory.resolve("users").toString());
        DocumentMapper mapper = mock(DocumentMapper.class);
        Map<Long, Document> rows = new HashMap<>();
        AtomicLong ids = new AtomicLong();
        when(mapper.insert(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(ids.incrementAndGet());
            rows.put(document.getId(), document);
            return 1;
        });
        when(mapper.selectById(any())).thenAnswer(invocation -> rows.get(invocation.getArgument(0)));
        FileContentExtractor extractor = mock(FileContentExtractor.class);
        when(extractor.getSupportedTypes()).thenReturn(Set.of("txt"));
        when(extractor.extractContent(any(), any())).thenReturn("content");
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getSetting(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        FileUploadService service = new FileUploadService(mapper, settings, extractor, storage, current);
        ReflectionTestUtils.setField(service, "allowedTypes", "txt");

        Document a = service.uploadAndAnalyze(new MockMultipartFile(
                "file", "same.txt", "text/plain", "A".getBytes()));
        owner.set(202L);
        Document b = service.uploadAndAnalyze(new MockMultipartFile(
                "file", "same.txt", "text/plain", "B".getBytes()));

        assertThat(a.getUserId()).isEqualTo(101L);
        assertThat(b.getUserId()).isEqualTo(202L);
        assertThat(a.getFilePath()).isNull();
        assertThat(b.getFilePath()).isNull();
        owner.set(101L);
        assertThat(new String(service.readDocumentFile(a.getId()).bytes())).isEqualTo("A");
        service.deleteDocument(a.getId());
        owner.set(202L);
        assertThat(new String(service.readDocumentFile(b.getId()).bytes())).isEqualTo("B");
    }

    @Test
    void unknownOrForeignDocumentIdDoesNotTouchStorage() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        OwnedStorageResolver storage = mock(OwnedStorageResolver.class);
        CurrentUserContext current = () -> Optional.of(new UserContext(101L));
        FileUploadService service = new FileUploadService(
                mapper, mock(SystemSettingsService.class), mock(FileContentExtractor.class), storage, current);

        assertThrows(UserResourceNotFoundException.class, () -> service.readDocumentFile(999L));
        assertThrows(UserResourceNotFoundException.class, () -> service.deleteDocument(999L));
        verifyNoInteractions(storage);
    }

    @Test
    void legacySharedPathIsNeverUsedByOnlineRead() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        Document legacy = Document.builder()
                .id(12L)
                .userId(101L)
                .fileName("legacy.txt")
                .filePath("./data/documents/legacy.txt")
                .storageKey(null)
                .build();
        when(mapper.selectById(12L)).thenReturn(legacy);
        OwnedStorageResolver storage = mock(OwnedStorageResolver.class);
        CurrentUserContext current = () -> Optional.of(new UserContext(101L));
        FileUploadService service = new FileUploadService(
                mapper, mock(SystemSettingsService.class), mock(FileContentExtractor.class), storage, current);

        assertThrows(IOException.class, () -> service.readDocumentFile(12L));

        verifyNoInteractions(storage);
    }
}
