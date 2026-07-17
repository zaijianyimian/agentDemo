@ApplicationModule(
        id = "mcp",
        displayName = "MCP",
        allowedDependencies = {"chat::*", "infrastructure", "schedule::*", "shared::*"}
)
package com.example.demo.mcp;

import org.springframework.modulith.ApplicationModule;
