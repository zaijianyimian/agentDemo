@ApplicationModule(
        id = "schedule",
        displayName = "Schedule",
        allowedDependencies = {"auth::*", "chat::*", "email::*", "infrastructure", "memory::*", "model::*", "shared::*"}
)
package com.example.demo.schedule;

import org.springframework.modulith.ApplicationModule;
