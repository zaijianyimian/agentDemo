@ApplicationModule(
        id = "note",
        displayName = "Note",
        allowedDependencies = {"infrastructure", "shared::*"}
)
package com.example.demo.note;

import org.springframework.modulith.ApplicationModule;
