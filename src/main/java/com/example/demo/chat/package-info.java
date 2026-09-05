@ApplicationModule(
        id = "chat",
        displayName = "Chat",
        allowedDependencies = {"email::tools", "mcp::*", "memory::*", "model::*", "note::*", "schedule::*", "search::*", "shared::*", "task::*"}
)
package com.example.demo.chat;

import org.springframework.modulith.ApplicationModule;
