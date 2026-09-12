package com.example.demo.dispatch.application;

import com.example.demo.system.application.SystemSettingsService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutorToggleServiceTest {

    @Test
    void initializesAndReportsOpenClawSetting() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getBooleanSetting("dispatch", "executor.openclaw.enabled", true)).thenReturn(true);
        ExecutorToggleService service = new ExecutorToggleService(settings);

        service.initDefaults();

        verify(settings).setSetting("dispatch", "executor.openclaw.enabled", "true");
        assertTrue(service.snapshot().get("openclaw"));
    }

    @Test
    void replacesOpenClawAndIgnoresUnknownExecutor() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        ExecutorToggleService service = new ExecutorToggleService(settings);

        service.replaceAll(Map.of("openclaw", false, "unknown", true));

        verify(settings).setSetting("dispatch", "executor.openclaw.enabled", "false");
        verify(settings, never()).setSetting("dispatch", "executor.unknown.enabled", "true");
    }
}
