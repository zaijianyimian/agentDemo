@ApplicationModule(
        id = "inbox",
        displayName = "Inbox",
        allowedDependencies = {"email::*", "infrastructure", "note::*", "schedule::*", "shared::*", "task::*"}
)
package com.example.demo.inbox;

import org.springframework.modulith.ApplicationModule;
