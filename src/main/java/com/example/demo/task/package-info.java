@ApplicationModule(
        id = "task",
        displayName = "Task",
        allowedDependencies = {"chat::*", "email::*", "memory::*", "model::*", "shared::*", "skill::*", "system::*"}
)
package com.example.demo.task;

import org.springframework.modulith.ApplicationModule;
