@ApplicationModule(
        id = "note",
        displayName = "Note",
        allowedDependencies = {"infrastructure", "memory::*", "shared::*"}
)
package com.example.demo.note;

import org.springframework.modulith.ApplicationModule;
