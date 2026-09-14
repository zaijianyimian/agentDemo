package com.example.demo.infrastructure.storage;

import com.example.demo.DemoApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Dedicated maintenance entry point. It never infers an owner and starts no online producer. */
public final class LegacyOwnedFileMigrationCli {

    private LegacyOwnedFileMigrationCli() {
    }

    public static void main(String[] args) throws Exception {
        long ownerUserId = requirePositiveLong("LEGACY_OWNER_USER_ID");
        String legacyPathBase = requireEnvironment("LEGACY_PATH_BASE");
        List<Path> inventoryRoots = Arrays.stream(requireEnvironment("LEGACY_FILE_INVENTORY_ROOTS").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .toList();
        if (inventoryRoots.isEmpty()) {
            throw new IllegalStateException("LEGACY_FILE_INVENTORY_ROOTS must list at least one directory");
        }
        try (var context = new SpringApplicationBuilder(DemoApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "app.task.scheduler.enabled=false",
                        "app.schedule.enabled=false",
                        "app.dispatch.enabled=false",
                        "app.email.listener.enabled=false",
                        "spring.rabbitmq.listener.simple.auto-startup=false")
                .run(args)) {
            var summary = context.getBean(LegacyOwnedFileMigrationService.class)
                    .migrate(ownerUserId, Path.of(legacyPathBase), inventoryRoots);
            System.out.printf("Legacy file migration completed: discovered=%d completed=%d%n",
                    summary.discovered(), summary.completed());
        }
    }

    private static long requirePositiveLong(String name) {
        String value = requireEnvironment(name);
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Fall through to the explicit error below.
        }
        throw new IllegalStateException(name + " must be a positive integer");
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set explicitly");
        }
        return value;
    }
}
