package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 搜索配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    /**
     * 搜索引擎类型: serper, tavily, bing, duckduckgo
     */
    private String engine = "serper";

    /**
     * 搜索API密钥
     */
    private String apiKey;

    /**
     * 搜索结果数量限制 (默认3条，节约成本)
     */
    private Integer maxResults = 3;

    /**
     * 是否启用搜索
     */
    private Boolean enabled = false;
}