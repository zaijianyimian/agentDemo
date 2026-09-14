@ApplicationModule(
        id = "inbox",
        displayName = "Inbox",
        allowedDependencies = {"email::*", "infrastructure", "schedule::*", "shared::*", "task::*"}
)
package com.example.demo.inbox;

import org.springframework.modulith.ApplicationModule;
