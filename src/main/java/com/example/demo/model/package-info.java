@ApplicationModule(
        id = "model",
        displayName = "Model",
        allowedDependencies = {"infrastructure", "shared::*"}
)
package com.example.demo.model;

import org.springframework.modulith.ApplicationModule;
