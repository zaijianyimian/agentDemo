@ApplicationModule(
        id = "chat",
        displayName = "Chat",
        allowedDependencies = {"infrastructure", "note::*", "schedule::*", "shared::*", "task::*"}
)
package com.example.demo.chat;

import org.springframework.modulith.ApplicationModule;
