@ApplicationModule(
        id = "search",
        displayName = "Search",
        allowedDependencies = {"chat::*", "infrastructure", "shared::*", "system::*"}
)
package com.example.demo.search;

import org.springframework.modulith.ApplicationModule;
