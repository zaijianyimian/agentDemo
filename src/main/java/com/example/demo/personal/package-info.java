@ApplicationModule(
        id = "personal",
        displayName = "Personal",
        allowedDependencies = {"chat::*", "code::*", "note::*", "schedule::*", "shared::*", "system::*", "task::*"}
)
package com.example.demo.personal;

import org.springframework.modulith.ApplicationModule;
