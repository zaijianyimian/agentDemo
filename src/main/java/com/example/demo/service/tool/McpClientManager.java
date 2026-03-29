package com.example.demo.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Server 连接管理器
 * 管理与外部 MCP Server 的连接
 * 通过 stdio 协议与 MCP Server 通信
 */
@Slf4j
@Service
public class McpClientManager {

    private final ObjectMapper objectMapper;

    /**
     * MCP Server 进程缓存：serverName -> Process
     */
    private final Map<String, Process> processCache = new ConcurrentHashMap<>();

    /**
     * MCP Server 输入流：serverName -> PrintWriter
     */
    private final Map<String, PrintWriter> writerCache = new ConcurrentHashMap<>();

    /**
     * MCP Server 输出流：serverName -> BufferedReader
     */
    private final Map<String, BufferedReader> readerCache = new ConcurrentHashMap<>();

    /**
     * 请求 ID 生成器
     */
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    public McpClientManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取或创建 MCP Server 连接
     *
     * @param serverName MCP Server 名称
     * @param config     MCP Server 配置（JSON格式）
     * @return 是否成功连接
     */
    public boolean getOrCreateConnection(String serverName, String config) {
        if (processCache.containsKey(serverName)) {
            return true;
        }

        try {
            McpServerConfig serverConfig = parseConfig(config);
            return startServer(serverName, serverConfig);
        } catch (Exception e) {
            log.error("Failed to create MCP connection: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 启动 MCP Server 进程
     */
    private boolean startServer(String serverName, McpServerConfig serverConfig) {
        try {
            // 构建进程命令
            List<String> command = new ArrayList<>();
            command.add(serverConfig.getCommand());
            if (serverConfig.getArgs() != null) {
                command.addAll(serverConfig.getArgs());
            }

            log.info("Starting MCP Server '{}' with command: {}", serverName, command);

            // 构建进程环境
            ProcessBuilder pb = new ProcessBuilder(command);
            if (serverConfig.getEnv() != null) {
                pb.environment().putAll(serverConfig.getEnv());
            }
            pb.redirectErrorStream(false);

            // 启动进程
            Process process = pb.start();
            processCache.put(serverName, process);

            // 获取输入输出流
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            writerCache.put(serverName, writer);
            readerCache.put(serverName, reader);

            // 启动错误流读取线程
            startErrorReader(serverName, process);

            // 初始化 MCP 连接
            if (!initializeConnection(serverName)) {
                log.error("Failed to initialize MCP connection for: {}", serverName);
                stopServer(serverName);
                return false;
            }

            log.info("MCP Server '{}' started successfully", serverName);
            return true;

        } catch (Exception e) {
            log.error("Failed to start MCP Server '{}': {}", serverName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 初始化 MCP 连接
     */
    private boolean initializeConnection(String serverName) {
        try {
            // 发送 initialize 请求
            Map<String, Object> initRequest = Map.of(
                    "jsonrpc", "2.0",
                    "id", requestIdGenerator.getAndIncrement(),
                    "method", "initialize",
                    "params", Map.of(
                            "protocolVersion", "2024-11-05",
                            "capabilities", Map.of(),
                            "clientInfo", Map.of(
                                    "name", "agent-demo",
                                    "version", "1.0.0"
                            )
                    )
            );

            String response = sendRequest(serverName, initRequest);
            log.info("MCP initialize response: {}", response);

            // 发送 initialized 通知
            Map<String, Object> initializedNotification = Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized"
            );
            sendNotification(serverName, initializedNotification);

            return response != null && !response.contains("error");

        } catch (Exception e) {
            log.error("Failed to initialize MCP connection: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 列出 MCP Server 提供的工具
     */
    public List<Map<String, Object>> listTools(String serverName) {
        try {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", requestIdGenerator.getAndIncrement(),
                    "method", "tools/list"
            );

            String response = sendRequest(serverName, request);
            if (response == null) {
                return List.of();
            }

            // 解析响应
            Map<String, Object> responseMap = objectMapper.readValue(response, new ObjectMapper().getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) responseMap.get("result");
            if (result == null) {
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            return tools != null ? tools : List.of();

        } catch (Exception e) {
            log.error("Failed to list tools: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 执行 MCP 工具
     *
     * @param serverName MCP Server 名称
     * @param toolName   工具名称
     * @param arguments  工具参数
     * @return 执行结果
     */
    public String executeTool(String serverName, String toolName, Map<String, Object> arguments) {
        try {
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", requestIdGenerator.getAndIncrement(),
                    "method", "tools/call",
                    "params", Map.of(
                            "name", toolName,
                            "arguments", arguments != null ? arguments : Map.of()
                    )
            );

            return sendRequest(serverName, request);

        } catch (Exception e) {
            log.error("Failed to execute tool '{}': {}", toolName, e.getMessage(), e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 发送请求并获取响应
     */
    private String sendRequest(String serverName, Map<String, Object> request) {
        try {
            PrintWriter writer = writerCache.get(serverName);
            BufferedReader reader = readerCache.get(serverName);

            if (writer == null || reader == null) {
                log.error("MCP Server '{}' not connected", serverName);
                return null;
            }

            String jsonRequest = objectMapper.writeValueAsString(request);
            log.debug("Sending to MCP Server '{}': {}", serverName, jsonRequest);

            writer.println(jsonRequest);

            // 读取响应
            String response = reader.readLine();
            log.debug("Received from MCP Server '{}': {}", serverName, response);

            return response;

        } catch (Exception e) {
            log.error("Failed to send request to MCP Server '{}': {}", serverName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 发送通知（无需响应）
     */
    private void sendNotification(String serverName, Map<String, Object> notification) {
        try {
            PrintWriter writer = writerCache.get(serverName);
            if (writer == null) {
                return;
            }

            String jsonNotification = objectMapper.writeValueAsString(notification);
            log.debug("Sending notification to MCP Server '{}': {}", serverName, jsonNotification);
            writer.println(jsonNotification);

        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    /**
     * 启动错误流读取线程
     */
    private void startErrorReader(String serverName, Process process) {
        Thread errorThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    log.warn("[MCP Server '{}'] stderr: {}", serverName, line);
                }
            } catch (IOException e) {
                log.debug("Error reading stderr for MCP Server '{}': {}", serverName, e.getMessage());
            }
        }, "MCP-ErrorReader-" + serverName);
        errorThread.setDaemon(true);
        errorThread.start();
    }

    /**
     * 解析 MCP Server 配置
     */
    private McpServerConfig parseConfig(String config) {
        try {
            return objectMapper.readValue(config, McpServerConfig.class);
        } catch (IOException e) {
            log.error("Failed to parse MCP Server config: {}", e.getMessage());
            throw new RuntimeException("解析 MCP Server 配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止 MCP Server
     */
    public void stopServer(String serverName) {
        Process process = processCache.remove(serverName);
        writerCache.remove(serverName);
        readerCache.remove(serverName);

        if (process != null) {
            process.destroy();
            log.info("MCP Server '{}' stopped", serverName);
        }
    }

    /**
     * 检查 MCP Server 是否已连接
     */
    public boolean isConnected(String serverName) {
        Process process = processCache.get(serverName);
        return process != null && process.isAlive();
    }

    /**
     * 获取所有连接的 Server 名称
     */
    public List<String> getConnectedServers() {
        return new ArrayList<>(processCache.keySet());
    }

    /**
     * 关闭所有连接
     */
    @PreDestroy
    public void closeAll() {
        log.info("Closing all MCP Server connections...");
        for (String serverName : new ArrayList<>(processCache.keySet())) {
            stopServer(serverName);
        }
    }

    /**
     * MCP Server 配置结构
     */
    public static class McpServerConfig {
        private String command;
        private List<String> args;
        private Map<String, String> env;

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }
    }
}