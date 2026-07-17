package com.example.demo.dispatch.web;

import com.example.demo.dispatch.application.WorkspaceManager;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.persistence.PushConfigMapper;
import com.example.demo.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 推送配置 REST API。单行 upsert。
 */
@RestController
@RequestMapping("/api/dispatch/push-config")
@RequiredArgsConstructor
public class PushConfigController {

    private final PushConfigMapper mapper;
    private final WorkspaceManager workspaceManager;

    @GetMapping
    /**
     * 获取当前推送配置（数据库中只存在单行）。
     */
    public ApiResponse<PushConfig> get() {
        return ApiResponse.success(workspaceManager.loadPushConfig());
    }

    @PutMapping
    /**
     * 整行覆盖更新推送配置（upsert 语义）。
     */
    public ApiResponse<PushConfig> update(@RequestBody PushConfig body) {
        body.setId(PushConfig.SINGLETON_ID);
        PushConfig existing = mapper.selectById(PushConfig.SINGLETON_ID);
        if (existing == null) {
            mapper.insert(body);
        } else {
            mapper.updateById(body);
        }
        return ApiResponse.success(mapper.selectById(PushConfig.SINGLETON_ID));
    }
}
