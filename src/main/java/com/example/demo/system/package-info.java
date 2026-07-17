@ApplicationModule(
        id = "system",
        displayName = "System",
        allowedDependencies = {"infrastructure", "shared::*", "task::*"}
)
package com.example.demo.system;

import org.springframework.modulith.ApplicationModule;
