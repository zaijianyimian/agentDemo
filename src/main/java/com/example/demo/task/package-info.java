@ApplicationModule(
        id = "task",
        displayName = "Task",
        allowedDependencies = {"auth::application", "email::*", "infrastructure", "shared::*", "system::*"}
)
package com.example.demo.task;

import org.springframework.modulith.ApplicationModule;
