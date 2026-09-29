package com.example.demo;

import com.example.demo.auth.application.AuthService;
import com.example.demo.email.application.EmailListenerService;
import com.example.demo.infrastructure.config.SecurityConfig;
import com.example.demo.schedule.application.ScheduleEventService;
import com.example.demo.shared.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithStructureTest {

    @Test
    void discoversApplicationModules() {
        ApplicationModules modules = ApplicationModules.of(DemoApplication.class);

        assertThat(modules).isNotNull();
        assertThat(modules.stream()).isNotEmpty();
    }

    @Test
    void discoversFeatureApplicationModules() {
        ApplicationModules modules = ApplicationModules.of(DemoApplication.class);
        Set<String> moduleNames = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());

        assertThat(moduleNames)
                .contains("auth", "email", "schedule", "task", "file",
                        "inbox", "system", "shared", "infrastructure", "dispatch")
                .doesNotContain("chat", "personal")
                .doesNotContain("app", "knowledge", "mcp", "skill", "memory", "autonomy");

        assertThat(modules.getModuleByName("auth"))
                .hasValueSatisfying(module -> assertThat(module.contains(AuthService.class)).isTrue());
        assertThat(modules.getModuleByName("email"))
                .hasValueSatisfying(module -> assertThat(module.contains(EmailListenerService.class)).isTrue());
        assertThat(modules.getModuleByName("schedule"))
                .hasValueSatisfying(module -> assertThat(module.contains(ScheduleEventService.class)).isTrue());
        assertThat(modules.getModuleByName("shared"))
                .hasValueSatisfying(module -> assertThat(module.contains(ApiResponse.class)).isTrue());
        assertThat(modules.getModuleByName("infrastructure"))
                .hasValueSatisfying(module -> assertThat(module.contains(SecurityConfig.class)).isTrue());
    }

    @Test
    void verifiesApplicationModuleBoundaries() {
        ApplicationModules.of(DemoApplication.class).verify();
    }
}
