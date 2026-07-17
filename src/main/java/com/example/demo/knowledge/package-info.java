@ApplicationModule(
        id = "knowledge",
        displayName = "Knowledge",
        allowedDependencies = {"file::*", "infrastructure", "memory::*", "shared::*", "system::*"}
)
package com.example.demo.knowledge;

import org.springframework.modulith.ApplicationModule;
