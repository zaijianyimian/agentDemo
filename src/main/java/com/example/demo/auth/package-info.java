@ApplicationModule(
        id = "auth",
        displayName = "Auth",
        allowedDependencies = {"email::*", "infrastructure", "shared::*"}
)
package com.example.demo.auth;

import org.springframework.modulith.ApplicationModule;
