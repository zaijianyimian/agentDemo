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
import java.util.stream.Collectors;

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
     * 支持多种分词策略：
     * 1. 按空格和标点分词（基础分词）
     * 2. 提取英文单词
     * 3. 提取中文词汇（基于常见词汇表）
     * 4. 提取技术术语（如 Java, Python, API 等）
     */
    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();

        if (query == null || query.isEmpty()) {
            return keywords;
        }

        // 1. 按空格和标点分词（基础分词）
        String[] parts = query.split("[\\s,，。！？;；：:\"\"''【】\\[\\]()（）\\t\\n\\r]+");
        for (String part : parts) {
            part = part.trim();
            if (part.length() >= 2 && part.length() <= 20) {
                keywords.add(part);
            }
        }

        // 2. 提取英文单词（连续字母序列）
        Pattern englishPattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{1,19}");
        Matcher englishMatcher = englishPattern.matcher(query);
        while (englishMatcher.find()) {
            String word = englishMatcher.group();
            if (!keywords.contains(word)) {
                keywords.add(word);
            }
        }

        // 3. 提取中文词汇（基于常见词汇表和 N-gram）
        extractChineseKeywords(query, keywords);

        // 4. 提取技术术语（专业关键词）
        extractTechnicalTerms(query, keywords);

        // 如果没有分出关键词，整体作为一个关键词
        if (keywords.isEmpty() && query.length() >= 2 && query.length() <= 20) {
            keywords.add(query.trim());
        }

        // 去重并限制数量
        return keywords.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 提取中文词汇
     * 使用 N-gram 方法提取可能的中国词汇
     */
    private void extractChineseKeywords(String text, List<String> keywords) {
        // 中文词汇通常在 2-4 个字之间
        // 使用简单的 N-gram 方法，结合常见词汇表验证

        // 常见中文词汇表（可扩展）
        Set<String> commonWords = Set.of(
                "人工智能", "机器学习", "深度学习", "大数据", "云计算",
                "软件开发", "前端开发", "后端开发", "数据库", "移动开发",
                "股票投资", "财经新闻", "商业管理", "市场营销",
                "健康养生", "运动健身", "医疗保健", "营养饮食",
                "学习教育", "在线课程", "职业培训", "技能提升",
                "旅游景点", "酒店预订", "机票出行", "签证攻略",
                "时事新闻", "热点事件", "政策解读", "国际关系"
        );

        // 检查是否包含常见词汇
        for (String word : commonWords) {
            if (text.contains(word) && !keywords.contains(word)) {
                keywords.add(word);
            }
        }

        // 提取 2-4 字的中文序列（N-gram）
        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}");
        Matcher chineseMatcher = chinesePattern.matcher(text);
        while (chineseMatcher.find()) {
            String word = chineseMatcher.group();
            if (!keywords.contains(word) && isValidChineseWord(word)) {
                keywords.add(word);
            }
        }
    }

    /**
     * 验证是否是有效的中文词汇
     */
    private boolean isValidChineseWord(String word) {
        // 简单验证：长度合理且不包含明显无意义的组合
        if (word.length() < 2 || word.length() > 4) {
            return false;
        }

        // 排除常见的无意义组合（可根据需要扩展）
        Set<String> invalidPatterns = Set.of("的的", "是是", "在在", "了了", "和和");
        return !invalidPatterns.contains(word);
    }

    /**
     * 提取技术术语
     */
    private void extractTechnicalTerms(String text, List<String> keywords) {
        // 技术术语列表
        Set<String> techTerms = Set.of(
                "AI", "API", "SDK", "REST", "JSON", "XML", "HTTP", "HTTPS",
                "SQL", "NoSQL", "ORM", "MVC", "MVVM", "IOC", "DI",
                "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust",
                "React", "Vue", "Angular", "Spring", "Django", "Flask",
                "MySQL", "PostgreSQL", "MongoDB", "Redis", "Elasticsearch",
                "Docker", "Kubernetes", "Git", "GitHub", "CI/CD",
                "WebSocket", "GraphQL", "OAuth", "JWT", "SSO"
        );

        for (String term : techTerms) {
            // 大小写不敏感匹配
            if (text.toLowerCase().contains(term.toLowerCase()) && !keywords.contains(term)) {
                keywords.add(term);
            }
        }
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