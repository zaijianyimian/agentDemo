@ApplicationModule(
        id = "task",
        displayName = "Task",
        allowedDependencies = {"chat::*", "email::*", "infrastructure", "shared::*", "system::*"}
)
package com.example.demo.task;

import org.springframework.modulith.ApplicationModule;
