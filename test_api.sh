#!/bin/bash
# API测试脚本 - AI Agent Demo
# 测试时间: 2026-03-28

BASE_URL="http://localhost:8000/api"
OUTPUT_FILE="D:/javaproject/agentDemo/test_results.md"

# 初始化测试文档
init_doc() {
    echo "# AI Agent Demo API测试报告" > "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "**测试时间**: 2026-03-28" >> "$OUTPUT_FILE"
    echo "**Base URL**: $BASE_URL" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "---" >> "$OUTPUT_FILE"
}

# 测试函数 - 记录请求、响应和状态
test_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local description="$5"

    echo "" >> "$OUTPUT_FILE"
    echo "## $name" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "**描述**: $description" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"

    # 记录请求
    echo "### 请求" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "**方法**: $method" >> "$OUTPUT_FILE"
    echo "**URL**: $url" >> "$OUTPUT_FILE"
    if [ -n "$data" ]; then
        echo "**请求体**:" >> "$OUTPUT_FILE"
        echo '```json' >> "$OUTPUT_FILE"
        echo "$data" >> "$OUTPUT_FILE"
        echo '```' >> "$OUTPUT_FILE"
    fi
    echo "" >> "$OUTPUT_FILE"

    # 执行请求并记录响应
    echo "### 响应" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"

    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$url" 2>&1)
    else
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X "$method" -H "Content-Type: application/json" -d "$data" "$url" 2>&1)
    fi

    http_code=$(echo "$response" | grep "HTTP_STATUS:" | cut -d: -f2)
    body=$(echo "$response" | grep -v "HTTP_STATUS:")

    echo "**HTTP状态码**: $http_code" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "**响应体**:" >> "$OUTPUT_FILE"
    echo '```json' >> "$OUTPUT_FILE"
    echo "$body" >> "$OUTPUT_FILE"
    echo '```' >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"

    # 判断是否成功
    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        echo "**测试结果**: ✅ 成功" >> "$OUTPUT_FILE"
    else
        echo "**测试结果**: ❌ 错误 (HTTP $http_code)" >> "$OUTPUT_FILE"
    fi

    echo "" >> "$OUTPUT_FILE"
    echo "---" >> "$OUTPUT_FILE"
}

# 初始化文档
init_doc

echo "开始API测试..."

# ==================== 聊天接口测试 ====================
echo "测试聊天接口..."

test_api "普通聊天" "GET" "$BASE_URL/chat/complete?message=你好" "" "返回完整响应文本"

test_api "结构化聊天" "GET" "$BASE_URL/chat/structured?message=如何学习Java" "" "返回含元数据的完整响应"

test_api "内容分析" "GET" "$BASE_URL/analyze?content=这是一封重要的邮件，需要立即处理" "" "分析文本的重要程度、标签等"

# ==================== 向量化接口测试 ====================
echo "测试向量化接口..."

test_api "文本向量化" "GET" "$BASE_URL/embedding?text=你好世界" "" "返回文本的向量数组"

test_api "文本向量化(完整)" "GET" "$BASE_URL/embedding/full?text=测试文本" "" "返回完整的向量化响应"

# ==================== 搜索接口测试 ====================
echo "测试搜索接口..."

test_api "网络搜索" "GET" "$BASE_URL/search?query=Java教程" "" "执行网络搜索"

test_api "搜索+AI总结" "GET" "$BASE_URL/search/summary?query=Spring Boot 3新特性" "" "搜索并返回AI总结"

test_api "带搜索聊天" "GET" "$BASE_URL/search/chat?message=2024年世界杯在哪里举办" "" "先搜索再回答(结构化)"

# ==================== 邮件配置接口测试 ====================
echo "测试邮件配置接口..."

test_api "邮箱配置列表" "GET" "$BASE_URL/email/config/list" "" "获取所有邮箱配置"

test_api "添加邮箱配置" "POST" "$BASE_URL/email/config" '{"email":"test@qq.com","password":"test_auth_code","host":"imap.qq.com","protocol":"imap","port":993,"sslEnabled":true,"enabled":false,"folder":"INBOX","pollInterval":30,"remark":"测试邮箱"}' "添加邮箱配置"

test_api "邮箱服务器模板" "GET" "$BASE_URL/email/templates" "" "获取常用邮箱服务器配置"

test_api "监听状态" "GET" "$BASE_URL/email/listener/status" "" "获取监听状态"

# ==================== MCP工具接口测试 ====================
echo "测试MCP工具接口..."

test_api "MCP工具列表" "GET" "$BASE_URL/mcp/tools" "" "获取所有MCP工具"

test_api "MCP启用工具" "GET" "$BASE_URL/mcp/tools/enabled" "" "获取启用的MCP工具"

test_api "添加MCP工具" "POST" "$BASE_URL/mcp/tools" '{"name":"test_tool","displayName":"测试工具","description":"这是一个测试工具","toolType":"http_api","config":"{\"url\":\"https://api.example.com\",\"method\":\"GET\",\"timeout\":30}","inputSchema":"{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}","enabled":false}' "添加新MCP工具"

test_api "MCP Agent对话" "GET" "$BASE_URL/mcp/agent/chat?message=你好" "" "AI自动调用工具对话"

# ==================== 技能接口测试 ====================
echo "测试技能接口..."

test_api "技能列表" "GET" "$BASE_URL/skill/list" "" "获取所有技能"

test_api "内置技能" "GET" "$BASE_URL/skill/builtin" "" "获取内置技能"

test_api "技能分类" "GET" "$BASE_URL/skill/categories" "" "获取技能分类"

test_api "添加技能" "POST" "$BASE_URL/skill" '{"code":"test_skill","name":"测试技能","description":"测试技能描述","category":"custom","icon":"star","enabled":true}' "添加新技能"

# ==================== 日程接口测试 ====================
echo "测试日程接口..."

test_api "日程列表" "GET" "$BASE_URL/schedule/list" "" "获取所有日程"

test_api "今日日程" "GET" "$BASE_URL/schedule/today" "" "获取今天的日程"

test_api "明日日程" "GET" "$BASE_URL/schedule/tomorrow" "" "获取明天的日程"

test_api "指定日期日程" "GET" "$BASE_URL/schedule/date/2026-03-28" "" "获取指定日期日程"

test_api "添加日程" "POST" "$BASE_URL/schedule" '{"title":"测试会议","description":"这是一个测试日程","eventTime":"2026-03-28T15:00:00","location":"会议室B","reminderEnabled":true}' "添加日程"

test_api "解析邮件日程" "POST" "$BASE_URL/schedule/parse-email" '{"subject":"会议通知","from":"manager@example.com","content":"请于明天下午2点参加项目评审会议，地点在会议室A"}' "从邮件提取日程"

echo "测试完成！结果已保存到 $OUTPUT_FILE"