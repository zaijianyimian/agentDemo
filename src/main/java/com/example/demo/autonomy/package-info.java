@ApplicationModule(
        id = "autonomy",
        displayName = "Autonomy",
        allowedDependencies = {"chat::*", "infrastructure", "model::*", "shared::*"}
)
package com.example.demo.autonomy;

import org.springframework.modulith.ApplicationModule;
