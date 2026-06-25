package com.example.demo.service.search;

import com.example.demo.entity.SearchHistory;
import com.example.demo.mapper.SearchHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 搜索历史服务
 */
@Slf4j
@Service
public class SearchHistoryService {

    private final SearchHistoryMapper historyMapper;

    public SearchHistoryService(SearchHistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    /**
     * 记录搜索历史
     */
    @Transactional
    public SearchHistory recordSearch(String query, String searchMode,
                                       int resultCount, boolean hasSummary,
                                       long durationMs, String sessionId) {
        SearchHistory history = SearchHistory.builder()
                .query(query)
                .searchMode(searchMode)
                .resultCount(resultCount)
                .hasSummary(hasSummary)
                .durationMs(durationMs)
                .sessionId(sessionId)
                .build();

        historyMapper.insert(history);
        log.debug("记录搜索历史: {}", query);
        return history;
    }

    /**
     * 获取最近的搜索历史
     */
    public List<SearchHistory> getRecentHistory(int limit) {
        return historyMapper.selectRecent(limit);
    }

    /**
     * 获取热门搜索关键词
     */
    public List<Map<String, Object>> getHotQueries(int limit) {
        return historyMapper.selectHotQueries(limit);
    }

    /**
     * 清空搜索历史
     */
    @Transactional
    public void clearHistory() {
        historyMapper.delete(null);
        log.info("清空搜索历史");
    }

    /**
     * 删除指定历史记录
     */
    @Transactional
    public void deleteHistory(Long id) {
        historyMapper.deleteById(id);
    }

    /**
     * 搜索历史统计
     */
    public Map<String, Object> getStatistics() {
        Long total = historyMapper.selectCount(null);
        List<Map<String, Object>> hotQueries = historyMapper.selectHotQueries(5);
        List<SearchHistory> recent = historyMapper.selectRecent(10);

        return Map.of(
                "totalSearches", total,
                "hotQueries", hotQueries,
                "recentSearches", recent
        );
    }
}