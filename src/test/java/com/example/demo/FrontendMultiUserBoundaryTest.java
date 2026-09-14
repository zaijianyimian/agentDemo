package com.example.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Source guardrails for account switching and ordinary-user UI capabilities. */
class FrontendMultiUserBoundaryTest {

    private static final Path FRONTEND = Path.of("frontend/src");

    @Test
    void sessionGenerationCancelsAndRejectsPreviousUserWork() throws IOException {
        String lifecycle = source("services/session-lifecycle.ts");
        String api = source("services/api/index.ts");
        String app = source("App.vue");

        assertThat(lifecycle).contains("generation += 1", "controller.abort()", "isCurrentSession");
        assertThat(api).contains("registerSessionController", "Discarded response from a previous user session");
        assertThat(app).contains(":key=\"authStore.sessionGeneration\"");
    }

    @Test
    void businessCachesAndStreamsBindSchemaOwnerAndGeneration() throws IOException {
        String preferences = source("services/user-preferences.ts");
        String events = source("services/email-events.ts");

        assertThat(preferences).contains("agent-demo:${BUSINESS_CACHE_SCHEMA}:user:${userId}");
        assertThat(preferences).contains("localStorage.removeItem(ACTION_LOG_KEY)");
        assertThat(events).contains("isCurrentSession(snapshot)");
        assertThat(events).contains("String(envelope.user_id) !== snapshot.userId");
    }

    @Test
    void ordinarySettingsExposeNoPlatformMutationOrUnsafeExecutor() throws IOException {
        String settings = source("views/Settings.vue");

        assertThat(settings).contains("WORKER_UNAVAILABLE", "本机外观偏好");
        assertThat(settings).doesNotContain(
                "settingsService", "backupService", "exportData", "importData",
                "Claude", "Codex", "OpenClaw", "updateSystem", "updateQdrant");
    }

    @Test
    void userFileTypesDoNotExposeLegacyPhysicalPaths() throws IOException {
        String types = source("types/index.ts");
        String documentBlock = types.substring(types.indexOf("export interface Document"),
                types.indexOf("// 日程事件类型"));
        String scheduleBlock = types.substring(types.indexOf("export interface ScheduleEvent"),
                types.indexOf("// 邮件配置类型"));
        String attachments = source("services/api/email-attachment.ts");

        assertThat(documentBlock).contains("storageKey").doesNotContain("filePath");
        assertThat(scheduleBlock).contains("storageKey").doesNotContain("filePath");
        assertThat(attachments).contains("storageKey", "storage_key").doesNotContain("filePath");
    }

    private static String source(String relative) throws IOException {
        return Files.readString(FRONTEND.resolve(relative));
    }
}
