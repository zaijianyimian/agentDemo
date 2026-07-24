package com.example.demo.dispatch.application.executor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutorRouterTest {

    @Test
    void routesOpenClawAndIncludesAvailability() {
        Executor claude = executor("claude-code", true);
        Executor openClaw = executor("openclaw", false);
        ExecutorRouter router = new ExecutorRouter(List.of(claude, openClaw));

        assertSame(openClaw, router.pick("openclaw"));
        assertEquals(false, router.availabilitySnapshot().get("openclaw"));
        assertSame(openClaw, router.pickFallback("claude-code"));
        assertFalse(router.anyAvailable() && router.availabilitySnapshot().get("openclaw"));
    }

    private Executor executor(String hint, boolean available) {
        return new Executor() {
            @Override
            public String hint() {
                return hint;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public String execute(com.example.demo.dispatch.domain.DispatchedTask task,
                                  String prompt, Path workspace, int timeoutSeconds) {
                return "ok";
            }
        };
    }
}
