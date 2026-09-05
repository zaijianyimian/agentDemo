@ApplicationModule(
        id = "mcp",
        displayName = "MCP",
        allowedDependencies = {"chat::*", "email::tools", "infrastructure", "schedule::*", "shared::*"}
)
package com.example.demo.mcp;

import org.springframework.modulith.ApplicationModule;
