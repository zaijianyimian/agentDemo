@ApplicationModule(
        id = "memory",
        displayName = "Memory",
        allowedDependencies = {"infrastructure", "shared::*", "system::*"}
)
package com.example.demo.memory;

import org.springframework.modulith.ApplicationModule;
