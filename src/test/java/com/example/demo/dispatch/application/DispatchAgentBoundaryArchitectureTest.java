package com.example.demo.dispatch.application;

import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.infrastructure.graph.GraphGatewayClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 防止已迁移到 Python 的 Agent Runtime 能力重新进入 Java dispatch。 */
class DispatchAgentBoundaryArchitectureTest {

    private static final Set<String> REMOVED_AGENT_RUNTIME_CLASSES = Set.of(
            "com.example.demo.dispatch.application.decision.DecisionRouter",
            "com.example.demo.dispatch.application.decision.HintDetector",
            "com.example.demo.dispatch.application.decision.ImportanceClassifier",
            "com.example.demo.dispatch.application.fallback.DecisionLayerFallback",
            "com.example.demo.dispatch.application.prompt.EmailMetadata",
            "com.example.demo.dispatch.application.prompt.HintMerger",
            "com.example.demo.dispatch.application.prompt.MemoryRecallService",
            "com.example.demo.dispatch.application.prompt.PromptTemplate"
    );

    @Test
    void removedAgentRuntimeClassesStayOutOfJava() {
        for (String className : REMOVED_AGENT_RUNTIME_CLASSES) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(className), className);
        }
    }

    @Test
    void graphGatewayDoesNotExposeDispatchMemoryOrSelfExecution() {
        Set<String> methods = methodNames(GraphGatewayClient.class);

        assertFalse(methods.contains("recallMemory"));
        assertFalse(methods.contains("completeDispatch"));
    }

    @Test
    void executorRouterOnlyMapsAnExplicitExecutorName() {
        Set<String> methods = methodNames(ExecutorRouter.class);

        assertNotNull(findMethod(ExecutorRouter.class, "pick", String.class));
        assertFalse(methods.contains("pickFallback"));
    }

    @Test
    void dispatchedTaskCarriesTheCompletePythonExecutionContract() {
        assertNotNull(findMethod(DispatchedTask.class, "getExecutor"));
        assertNotNull(findMethod(DispatchedTask.class, "getExecutionInstruction"));
        assertNotNull(findMethod(DispatchedTask.class, "getRetryMax"));
        assertNotNull(findMethod(DispatchedTask.class, "getExecutorTimeoutSeconds"));

        Set<String> methods = methodNames(DispatchedTask.class);
        assertFalse(methods.contains("getExecutorHint"));
        assertFalse(methods.contains("getFallbackExecutor"));
        assertFalse(methods.contains("getUserHint"));
        assertFalse(methods.contains("getFinalHint"));
    }

    @Test
    void postgresDriverIsNotPartOfTheJavaRuntime() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.postgresql.Driver"));
    }

    private static Set<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException error) {
            throw new AssertionError("missing method: " + type.getName() + "#" + name, error);
        }
    }
}
