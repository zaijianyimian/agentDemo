@ApplicationModule(
        id = "schedule",
        displayName = "Schedule",
        allowedDependencies = {"auth::*", "email::*", "infrastructure", "shared::*"}
)
package com.example.demo.schedule;

import org.springframework.modulith.ApplicationModule;
