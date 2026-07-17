@ApplicationModule(
        id = "report",
        displayName = "Report",
        allowedDependencies = {"autonomy::*", "chat::*", "note::*", "schedule::*", "search::*", "shared::*", "task::*"}
)
package com.example.demo.report;

import org.springframework.modulith.ApplicationModule;
