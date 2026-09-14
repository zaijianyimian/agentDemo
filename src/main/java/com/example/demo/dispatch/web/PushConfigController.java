package com.example.demo.dispatch.web;

import com.example.demo.dispatch.application.PushConfigService;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 当前用户推送配置 REST API。
 */
@RestController
@RequestMapping("/api/dispatch/push-config")
@RequiredArgsConstructor
public class PushConfigController {

    private final PushConfigService pushConfigs;

    @GetMapping
    /**
     * 获取当前用户推送配置。
     */
    public ApiResponse<PushConfig> get() {
        return ApiResponse.success(pushConfigs.getOrCreate());
    }

    @PutMapping
    /**
     * 整行覆盖更新推送配置（upsert 语义）。
     */
    public ApiResponse<PushConfig> update(@RequestBody PushConfig body) {
        return ApiResponse.success(pushConfigs.update(body));
    }
}
