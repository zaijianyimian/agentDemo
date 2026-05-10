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
import java.util.regex.Pattern;

/**
 * MCP Server 连接管理器
 * 管理与外部 MCP Server 的连接
 * 通过 stdio 协议与 MCP Server 通信
 *
 * 安全措施：
 * - 命令白名单验证
 * - 参数安全检查
 * - 防止命令注入
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

    /**
     * 允许的 MCP Server 命令白名单
     * 只允许常见的安全命令执行器
     */
    private static final List<String> ALLOWED_COMMANDS = List.of(
            "node", "nodejs", "python", "python3", "uvx", "npx",
            "java", "go", "ruby"
    );

    /**
     * 安全参数正则：只允许字母、数字、下划线、连字符、点、斜杠
     */
    private static final Pattern SAFE_ARG_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.\\/@]+$");

    /**
     * 危险字符黑名单
     */
    private static final String[] DANGEROUS_CHARS = {
            ";", "|", "&", "$", "`", "(", ")", "{", "}", "<", ">",
            "\n", "\r", "\\", "'", "\"", "~", "!", "*", "?"
    };

    public McpClientManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 验证命令是否在白名单中
     */
    private boolean isCommandAllowed(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        // 提取命令的基本名称（去掉路径）- 使用 final 变量
        final String baseCommand;
        if (command.contains("/")) {
            baseCommand = command.substring(command.lastIndexOf("/") + 1);
        } else if (command.contains("\\")) {
            baseCommand = command.substring(command.lastIndexOf("\\") + 1);
        } else {
            baseCommand = command;
        }
        // 检查是否在白名单中
        boolean allowed = ALLOWED_COMMANDS.stream()
                .anyMatch(allowedCmd -> baseCommand.equalsIgnoreCase(allowedCmd));
        if (!allowed) {
            log.warn("命令 '{}' 不在允许的白名单中", command);
        }
        return allowed;
    }

    /**
     * 验证参数是否安全
     */
    private boolean isArgSafe(String arg) {
        if (arg == null || arg.isEmpty()) {
            return true;
        }
        // 检查危险字符
        for (String dangerous : DANGEROUS_CHARS) {
            if (arg.contains(dangerous)) {
                log.warn("参数包含危险字符 '{}'，已拒绝: {}", dangerous, arg);
                return false;
            }
        }
        return SAFE_ARG_PATTERN.matcher(arg).matches();
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
            // 安全检查：验证命令是否在白名单中
            if (!isCommandAllowed(serverConfig.getCommand())) {
                log.error("命令 '{}' 不在允许的白名单中，拒绝启动 MCP Server '{}'",
                        serverConfig.getCommand(), serverName);
                return false;
            }

            // 安全检查：验证所有参数
            if (serverConfig.getArgs() != null) {
                for (String arg : serverConfig.getArgs()) {
                    if (!isArgSafe(arg)) {
                        log.error("参数包含危险字符，拒绝启动 MCP Server '{}': {}", serverName, arg);
                        return false;
                    }
                }
            }

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
                // 安全检查：过滤环境变量中的危险值
                for (Map.Entry<String, String> entry : serverConfig.getEnv().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    // 环境变量值不能包含命令注入字符
                    if (value != null && !value.contains(";") && !value.contains("|")
                            && !value.contains("&") && !value.contains("`")) {
                        pb.environment().put(key, value);
                    } else {
                        log.warn("环境变量 '{}' 包含危险字符，已跳过", key);
                    }
                }
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