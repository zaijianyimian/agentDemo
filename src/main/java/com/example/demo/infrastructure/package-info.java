@ApplicationModule(
        id = "infrastructure",
        displayName = "Infrastructure",
        allowedDependencies = {"shared::dto", "system::*"},
        type = ApplicationModule.Type.OPEN
)
package com.example.demo.infrastructure;

import org.springframework.modulith.ApplicationModule;
