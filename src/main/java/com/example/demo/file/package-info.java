@ApplicationModule(
        id = "file",
        displayName = "File",
        allowedDependencies = {"infrastructure", "shared::*", "system::*"}
)
package com.example.demo.file;

import org.springframework.modulith.ApplicationModule;
