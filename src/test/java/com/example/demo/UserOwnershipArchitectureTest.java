package com.example.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Source-level guardrails for the trusted owner boundary. */
class UserOwnershipArchitectureTest {
    private static final Path JAVA_SOURCES = Path.of("src/main/java/com/example/demo");

    @Test
    void controllersDoNotDependOnPersistenceMappers() throws IOException {
        for (Path controller : javaFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .toList()) {
            String source = Files.readString(controller);
            assertThat(source)
                    .as(controller.toString())
                    .doesNotContainPattern("import com\\.example\\.demo\\..*\\.persistence\\..*Mapper;");
        }
    }

    @Test
    void tenantBypassesRemainExplicitlyInternal() throws IOException {
        for (Path sourceFile : javaFiles()) {
            String source = Files.readString(sourceFile);
            int offset = 0;
            while ((offset = source.indexOf("@InterceptorIgnore", offset)) >= 0) {
                String declaration = source.substring(offset, Math.min(source.length(), offset + 500));
                assertThat(declaration)
                        .as(sourceFile + " tenant bypass")
                        .containsPattern("[A-Za-z0-9_]*Internal[A-Za-z0-9_]*\\s*\\(");
                offset += "@InterceptorIgnore".length();
            }
        }
    }

    @Test
    void duplicateUserThreadLocalsStayDeleted() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.example.demo.infrastructure.security.CurrentUserProvider"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.example.demo.infrastructure.security.UserExecutionContext"));
    }

    private static List<Path> javaFiles() throws IOException {
        try (var files = Files.walk(JAVA_SOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
