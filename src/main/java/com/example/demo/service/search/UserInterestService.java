package com.example.demo.service.search;

import com.example.demo.entity.UserInterest;
import com.example.demo.mapper.UserInterestMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户兴趣分析服务
 * 根据搜索历史分析用户兴趣
 */
@Slf4j
@Service
public class UserInterestService {

    private final UserInterestMapper interestMapper;
    private final ObjectMapper objectMapper;

    // 预定义的兴趣分类关键词
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "technology", Arrays.asList("AI", "人工智能", "编程", "代码", "软件", "开发", "Java", "Python",
                    "前端", "后端", "数据库", "云计算", "机器学习", "深度学习", "API", "框架", "技术", "互联网", "计算机"),
            "business", Arrays.asList("股票", "投资", "财经", "金融", "经济", "创业", "商业", "公司", "市场",
                    "营销", "管理", "财务", "理财", "基金", "银行", "汇率", "贸易"),
            "entertainment", Arrays.asList("电影", "音乐", "游戏", "娱乐", "明星", "综艺", "电视剧", "动漫",
                    "小说", "视频", "直播", "体育", "足球", "篮球", "赛事"),
            "health", Arrays.asList("健康", "医疗", "养生", "运动", "健身", "营养", "减肥", "疾病", "医院",
                    "药品", "保健", "心理", "睡眠", "饮食"),
            "education", Arrays.asList("学习", "教育", "考试", "考研", "大学", "课程", "培训", "知识",
                    "学校", "教学", "在线学习", "教程", "技能", "职业"),
            "travel", Arrays.asList("旅游", "景点", "酒店", "机票", "出行", "攻略", "签证", "度假", "自驾"),
            "news", Arrays.asList("新闻", "时事", "政治", "社会", "国际", "热点", "事件", "政策")
    );

    public UserInterestService(UserInterestMapper interestMapper, ObjectMapper objectMapper) {
        this.interestMapper = interestMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析搜索关键词并更新用户兴趣
     */
    @Transactional
    public void analyzeAndUpdateInterest(String query) {
        // 提取关键词
        List<String> keywords = extractKeywords(query);

        for (String keyword : keywords) {
            String category = categorizeKeyword(keyword);

            // 查找已存在的兴趣标签
            UserInterest existing = interestMapper.selectByTag(keyword);

            if (existing != null) {
                // 更新已存在的兴趣
                interestMapper.increaseWeight(keyword, 1);

                // 更新相关关键词
                try {
                    List<String> relatedKeywords = objectMapper.readValue(
                            existing.getRelatedKeywords() != null ? existing.getRelatedKeywords() : "[]",
                            new TypeReference<List<String>>() {}
                    );
                    if (!relatedKeywords.contains(query) && relatedKeywords.size() < 20) {
                        relatedKeywords.add(query);
                        existing.setRelatedKeywords(objectMapper.writeValueAsString(relatedKeywords));
                        interestMapper.updateById(existing);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("解析相关关键词失败", e);
                }
            } else {
                // 创建新的兴趣标签
                UserInterest newInterest = UserInterest.builder()
                        .tag(keyword)
                        .category(category)
                        .weight(1)
                        .searchCount(1)
                        .relatedKeywords(toJson(List.of(query)))
                        .lastSearchTime(LocalDateTime.now())
                        .build();
                interestMapper.insert(newInterest);
                log.info("发现新兴趣标签: {} (分类: {})", keyword, category);
            }
        }
    }

    /**
     * 提取搜索关键词
     */
    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();

        // 分词（简单实现，按空格和标点分）
        String[] parts = query.split("[\\s,，。！？;；：:\"\"''【】\\[\\]()（）]+");
        for (String part : parts) {
            if (part.length() >= 2 && part.length() <= 20) {
                keywords.add(part);
            }
        }

        // 如果没有分出关键词，整体作为一个关键词
        if (keywords.isEmpty() && query.length() >= 2 && query.length() <= 20) {
            keywords.add(query);
        }

        return keywords;
    }

    /**
     * 根据关键词判断分类
     */
    private String categorizeKeyword(String keyword) {
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String categoryKeyword : entry.getValue()) {
                if (keyword.contains(categoryKeyword) || categoryKeyword.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "other";
    }

    /**
     * 获取用户所有兴趣标签
     */
    public List<UserInterest> getAllInterests() {
        return interestMapper.selectAllOrderByWeight();
    }

    /**
     * 获取用户主要兴趣（Top N）
     */
    public List<UserInterest> getTopInterests(int limit) {
        return interestMapper.selectTopInterests(limit);
    }

    /**
     * 获取兴趣分析报告
     */
    public Map<String, Object> getInterestReport() {
        List<UserInterest> allInterests = interestMapper.selectAllOrderByWeight();

        // 按分类统计
        Map<String, Integer> categoryCount = new HashMap<>();
        Map<String, List<UserInterest>> categoryInterests = new HashMap<>();

        for (UserInterest interest : allInterests) {
            String category = interest.getCategory();
            categoryCount.merge(category, interest.getWeight(), Integer::sum);

            categoryInterests.computeIfAbsent(category, k -> new ArrayList<>()).add(interest);
        }

        // 计算总权重
        int totalWeight = allInterests.stream().mapToInt(UserInterest::getWeight).sum();

        // 找出主要兴趣领域
        List<Map.Entry<String, Integer>> sortedCategories = categoryCount.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        return Map.of(
                "totalInterests", allInterests.size(),
                "totalSearchWeight", totalWeight,
                "categoryDistribution", sortedCategories,
                "categoryInterests", categoryInterests,
                "topInterests", getTopInterests(10)
        );
    }

    /**
     * 清空用户兴趣数据
     */
    @Transactional
    public void clearInterests() {
        interestMapper.delete(null);
        log.info("清空用户兴趣数据");
    }

    /**
     * 删除指定兴趣标签
     */
    @Transactional
    public void deleteInterest(Long id) {
        interestMapper.deleteById(id);
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}