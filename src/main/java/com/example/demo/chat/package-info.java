@ApplicationModule(
        id = "chat",
        displayName = "Chat",
        allowedDependencies = {"mcp::*", "memory::*", "model::*", "note::*", "schedule::*", "search::*", "shared::*", "task::*"}
)
package com.example.demo.chat;

import org.springframework.modulith.ApplicationModule;
