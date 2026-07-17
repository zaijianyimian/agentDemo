@ApplicationModule(
        id = "email",
        displayName = "Email",
        allowedDependencies = {"infrastructure", "model::*", "shared::*"}
)
package com.example.demo.email;

import org.springframework.modulith.ApplicationModule;
