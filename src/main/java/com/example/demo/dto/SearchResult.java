package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 网络搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /**
     * 搜索结果标题
     */
    private String title;

    /**
     * 搜索结果链接
     */
    private String url;

    /**
     * 搜索结果摘要
     */
    private String snippet;

    /**
     * 来源网站
     */
    private String source;

    /**
     * 搜索结果列表
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResult {
        private String query;
        private List<SearchResult> results;
        private Integer totalResults;
    }
}