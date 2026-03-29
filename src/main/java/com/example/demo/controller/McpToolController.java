package com.example.demo.controller;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.service.mcp.McpToolService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具管理控制器
 */
@RestController
@RequestMapping("/api/mcp/tools")
public class McpToolController {

    @Resource
    private McpToolService mcpToolService;

    // ==================== 工具管理 ====================

    /**
     * 获取所有工具
     */
    @GetMapping
    public List<McpTool> listAll() {
        return mcpToolService.listAll();
    }

    /**
     * 获取启用的工具
     */
    @GetMapping("/enabled")
    public List<McpTool> listEnabled() {
        return mcpToolService.listEnabled();
    }

    /**
     * 获取工具详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<McpTool> getById(@PathVariable Long id) {
        McpTool tool = mcpToolService.getById(id);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tool);
    }

    /**
     * 添加工具
     */
    @PostMapping
    public ResponseEntity<String> add(@RequestBody McpTool tool) {
        try {
            mcpToolService.add(tool);
            return ResponseEntity.ok("添加成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 更新工具
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody McpTool tool) {
        try {
            tool.setId(id);
            mcpToolService.update(tool);
            return ResponseEntity.ok("更新成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 删除工具
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        mcpToolService.delete(id);
        return ResponseEntity.ok("删除成功");
    }

    /**
     * 切换启用状态
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<String> toggleEnabled(@PathVariable Long id) {
        try {
            mcpToolService.toggleEnabled(id);
            return ResponseEntity.ok("状态已切换");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具
     */
    @PostMapping("/{name}/execute")
    public ResponseEntity<ToolExecutionResult> execute(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> params) {
        ToolExecutionResult result = mcpToolService.execute(name, params != null ? params : Map.of());
        return ResponseEntity.ok(result);
    }

    /**
     * 测试工具
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<ToolExecutionResult> test(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        ToolExecutionResult result = mcpToolService.testTool(id, params != null ? params : Map.of());
        return ResponseEntity.ok(result);
    }

    // ==================== 工具验证 ====================

    /**
     * 验证工具配置
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<Map<String, Object>> validate(@PathVariable Long id) {
        McpTool tool = mcpToolService.getById(id);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        boolean valid = mcpToolService.validateConfig(tool);
        return ResponseEntity.ok(Map.of(
                "valid", valid,
                "toolName", tool.getName(),
                "toolType", tool.getToolType()
        ));
    }
}