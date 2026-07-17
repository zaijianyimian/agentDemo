@ApplicationModule(
        id = "chatimport",
        displayName = "Chat Import",
        allowedDependencies = {"chat::*", "infrastructure", "memory::*", "shared::*"}
)
package com.example.demo.chatimport;

import org.springframework.modulith.ApplicationModule;
