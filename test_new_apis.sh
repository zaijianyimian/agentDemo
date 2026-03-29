#!/bin/bash

# AI Agent Demo - 新增接口增量测试脚本
# Base URL: http://localhost:8000/api
# 编码: UTF-8

BASE_URL="http://localhost:8000/api"
CONTENT_TYPE="Content-Type: application/json; charset=UTF-8"
ACCEPT="Accept: application/json"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试结果统计
PASS_COUNT=0
FAIL_COUNT=0

# 测试函数
test_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local expected_status="$5"

    echo -e "${BLUE}[测试] ${name}${NC}"
    echo "请求: ${method} ${url}"

    if [ "$method" == "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" -X GET "${url}" -H "${ACCEPT}" 2>&1)
    elif [ "$method" == "POST" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST "${url}" -H "${CONTENT_TYPE}" -H "${ACCEPT}" -d "${data}" 2>&1)
    elif [ "$method" == "PUT" ]; then
        response=$(curl -s -w "\n%{http_code}" -X PUT "${url}" -H "${CONTENT_TYPE}" -H "${ACCEPT}" -d "${data}" 2>&1)
    elif [ "$method" == "DELETE" ]; then
        response=$(curl -s -w "\n%{http_code}" -X DELETE "${url}" -H "${ACCEPT}" 2>&1)
    fi

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expected_status" ]; then
        echo -e "${GREEN}[通过] HTTP状态码: ${http_code}${NC}"
        echo "响应: ${body}"
        ((PASS_COUNT++))
    else
        echo -e "${RED}[失败] HTTP状态码: ${http_code} (期望: ${expected_status})${NC}"
        echo "响应: ${body}"
        ((FAIL_COUNT++))
    fi
    echo "---"
}

echo "========================================"
echo "AI Agent Demo - 新增接口增量测试"
echo "========================================"
echo ""

# ==========================================
# 1. 会话历史API测试
# ==========================================
echo -e "${YELLOW}=== 1. 会话历史API测试 ===${NC}"

# 创建会话
SESSION_DATA='{"title":"测试会话"}'
test_api "创建会话" "POST" "${BASE_URL}/chat/history/session" "${SESSION_DATA}" "200"

# 获取会话列表
test_api "会话列表" "GET" "${BASE_URL}/chat/history/sessions" "" "200"

# 获取会话详情 (使用ID=1)
test_api "会话详情" "GET" "${BASE_URL}/chat/history/session/1" "" "200"

# 更新会话标题
test_api "更新会话标题" "PUT" "${BASE_URL}/chat/history/session/1/title" '{"title":"更新后的标题"}' "200"

# 添加消息到会话
MESSAGE_DATA='{"role":"user","content":"你好，这是一条测试消息"}'
test_api "添加消息到会话" "POST" "${BASE_URL}/chat/history/session/1/message" "${MESSAGE_DATA}" "200"

# 获取会话消息列表
test_api "会话消息列表" "GET" "${BASE_URL}/chat/history/session/1/messages" "" "200"

echo ""

# ==========================================
# 2. 记忆API测试 (UTF-8编码重点测试)
# ==========================================
echo -e "${YELLOW}=== 2. 记忆API测试 (UTF-8测试) ===${NC}"

# 记忆提取存储 - 使用UTF-8编码的中文数据
MEMORY_DATA='{"recentMessages":["用户说：我喜欢编程和人工智能","助手回复：很高兴认识你，这些领域很有趣"],"userId":"test-user"}'
test_api "记忆提取存储(中文)" "POST" "${BASE_URL}/memory/extract-store" "${MEMORY_DATA}" "200"

# 记忆搜索
test_api "记忆搜索" "GET" "${BASE_URL}/memory/search?query=编程&userId=test-user" "" "200"

echo ""

# ==========================================
# 3. 知识库API测试
# ==========================================
echo -e "${YELLOW}=== 3. 知识库API测试 ===${NC}"

# 创建知识库
KB_DATA='{"name":"测试知识库","description":"用于测试的知识库","type":"DOCUMENT"}'
test_api "创建知识库" "POST" "${BASE_URL}/knowledge" "${KB_DATA}" "200"

# 知识库列表
test_api "知识库列表" "GET" "${BASE_URL}/knowledge/list" "" "200"

# 知识库详情
test_api "知识库详情" "GET" "${BASE_URL}/knowledge/1" "" "200"

# 更新知识库
test_api "更新知识库" "PUT" "${BASE_URL}/knowledge/1" '{"name":"更新后的知识库","description":"更新后的描述"}' "200"

# 切换知识库状态
test_api "切换知识库状态" "PUT" "${BASE_URL}/knowledge/1/toggle" "" "200"

# 文档列表
test_api "文档列表" "GET" "${BASE_URL}/knowledge/1/documents" "" "200"

echo ""

# ==========================================
# 4. 定时任务API测试
# ==========================================
echo -e "${YELLOW}=== 4. 定时任务API测试 ===${NC}"

# 任务列表
test_api "任务列表" "GET" "${BASE_URL}/task/list" "" "200"

# 任务类型
test_api "任务类型" "GET" "${BASE_URL}/task/types" "" "200"

# 创建任务
TASK_DATA='{"name":"测试任务","taskType":"SKILL","cronExpression":"0 0 12 * * ?","skillCode":"web-search","enabled":true}'
test_api "创建任务" "POST" "${BASE_URL}/task" "${TASK_DATA}" "200"

# 任务详情
test_api "任务详情" "GET" "${BASE_URL}/task/1" "" "200"

# 更新任务
test_api "更新任务" "PUT" "${BASE_URL}/task/1" '{"name":"更新后的任务","taskType":"SKILL","cronExpression":"0 0 18 * * ?"}' "200"

# 切换任务状态
test_api "切换任务状态" "PUT" "${BASE_URL}/task/1/toggle" "" "200"

echo ""

# ==========================================
# 5. 模型配置API测试
# ==========================================
echo -e "${YELLOW}=== 5. 模型配置API测试 ===${NC}"

# 模型列表
test_api "模型列表" "GET" "${BASE_URL}/model/list" "" "200"

# 提供商列表
test_api "提供商列表" "GET" "${BASE_URL}/model/providers" "" "200"

# 创建模型配置
MODEL_DATA='{"name":"测试模型","provider":"OPENAI","apiKey":"test-key","baseUrl":"https://api.openai.com","modelName":"gpt-4","enabled":false}'
test_api "创建模型配置" "POST" "${BASE_URL}/model" "${MODEL_DATA}" "200"

# 模型详情
test_api "模型详情" "GET" "${BASE_URL}/model/1" "" "200"

# 更新模型配置
test_api "更新模型配置" "PUT" "${BASE_URL}/model/1" '{"name":"更新后的模型","modelName":"gpt-4-turbo"}' "200"

# 切换模型状态
test_api "切换模型状态" "PUT" "${BASE_URL}/model/1/toggle" "" "200"

echo ""

# ==========================================
# 6. 笔记API测试
# ==========================================
echo -e "${YELLOW}=== 6. 笔记API测试 ===${NC}"

# 笔记列表
test_api "笔记列表" "GET" "${BASE_URL}/note/list" "" "200"

# 创建笔记
NOTE_DATA='{"title":"测试笔记","content":"这是一条测试笔记内容，包含中文"}'
test_api "创建笔记" "POST" "${BASE_URL}/note" "${NOTE_DATA}" "200"

# 笔记详情
test_api "笔记详情" "GET" "${BASE_URL}/note/1" "" "200"

# 更新笔记
test_api "更新笔记" "PUT" "${BASE_URL}/note/1" '{"title":"更新后的笔记","content":"更新后的内容"}' "200"

# 切换置顶
test_api "切换置顶" "PUT" "${BASE_URL}/note/1/pin" "" "200"

# 搜索笔记
test_api "搜索笔记" "GET" "${BASE_URL}/note/search?keyword=测试" "" "200"

echo ""

# ==========================================
# 7. 代码片段API测试
# ==========================================
echo -e "${YELLOW}=== 7. 代码片段API测试 ===${NC}"

# 代码片段列表
test_api "代码片段列表" "GET" "${BASE_URL}/snippet/list" "" "200"

# 按语言查询
test_api "按语言查询(Java)" "GET" "${BASE_URL}/snippet/language/java" "" "200"

# 创建代码片段
SNIPPET_DATA='{"title":"Hello World","language":"java","code":"public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello World\"); } }","description":"Java Hello World示例"}'
test_api "创建代码片段" "POST" "${BASE_URL}/snippet" "${SNIPPET_DATA}" "200"

# 片段详情
test_api "片段详情" "GET" "${BASE_URL}/snippet/1" "" "200"

# 搜索片段
test_api "搜索片段" "GET" "${BASE_URL}/snippet/search?keyword=Hello" "" "200"

echo ""

# ==========================================
# 8. 日程API新增接口测试
# ==========================================
echo -e "${YELLOW}=== 8. 日程API新增接口测试 ===${NC}"

# 最近日程
test_api "最近日程" "GET" "${BASE_URL}/schedule/latest" "" "200"

# 今日日程
test_api "今日日程" "GET" "${BASE_URL}/schedule/today" "" "200"

# 明日日程
test_api "明日日程" "GET" "${BASE_URL}/schedule/tomorrow" "" "200"

# 日期范围日程
test_api "日期范围日程" "GET" "${BASE_URL}/schedule/range?startDate=2026-03-29&endDate=2026-04-05" "" "200"

# 添加日程
SCHEDULE_DATA='{"title":"测试日程","description":"这是一个测试日程","startTime":"2026-03-29T10:00:00","endTime":"2026-03-29T12:00:00"}'
test_api "添加日程" "POST" "${BASE_URL}/schedule" "${SCHEDULE_DATA}" "200"

# 取消日程
test_api "取消日程" "PUT" "${BASE_URL}/schedule/1/cancel" "" "200"

echo ""

# ==========================================
# 9. 技能API新增接口测试
# ==========================================
echo -e "${YELLOW}=== 9. 技能API新增接口测试 ===${NC}"

# 启用的技能
test_api "启用的技能" "GET" "${BASE_URL}/skill/enabled" "" "200"

# 内置技能
test_api "内置技能" "GET" "${BASE_URL}/skill/builtin" "" "200"

# 技能分类
test_api "技能分类" "GET" "${BASE_URL}/skill/categories" "" "200"

# 按分类获取技能
test_api "按分类获取技能" "GET" "${BASE_URL}/skill/category/信息检索" "" "200"

# 技能详情
test_api "技能详情" "GET" "${BASE_URL}/skill/1" "" "200"

echo ""

# ==========================================
# 10. 邮箱API新增接口测试
# ==========================================
echo -e "${YELLOW}=== 10. 邮箱API新增接口测试 ===${NC}"

# 启用的邮箱配置
test_api "启用的邮箱配置" "GET" "${BASE_URL}/email/config/enabled" "" "200"

# 邮箱配置详情
test_api "邮箱配置详情" "GET" "${BASE_URL}/email/config/1" "" "200"

# 邮箱服务器模板
test_api "邮箱服务器模板" "GET" "${BASE_URL}/email/templates" "" "200"

# 监听状态
test_api "监听状态" "GET" "${BASE_URL}/email/listener/status" "" "200"

echo ""

# ==========================================
# 11. 带会话记忆的聊天测试
# ==========================================
echo -e "${YELLOW}=== 11. 带会话记忆的聊天测试 ===${NC}"

# 带会话记忆聊天 (需要sessionId)
test_api "带会话记忆聊天" "GET" "${BASE_URL}/chat/complete/session?message=你好&sessionId=1" "" "200"

# 测试流式响应
test_api "测试流式响应" "GET" "${BASE_URL}/chat/stream/test?message=测试流式" "" "200"

echo ""

# ==========================================
# 12. 文件API测试
# ==========================================
echo -e "${YELLOW}=== 12. 文件API测试 ===${NC}"

# 文件列表
test_api "文件列表" "GET" "${BASE_URL}/file/list" "" "200"

# 按重要性搜索
test_api "按重要性搜索文件" "GET" "${BASE_URL}/file/search?importance=HIGH" "" "200"

echo ""

# ==========================================
# 测试结果汇总
# ==========================================
echo "========================================"
echo -e "${YELLOW}测试结果汇总${NC}"
echo "========================================"
echo -e "${GREEN}通过: ${PASS_COUNT}${NC}"
echo -e "${RED}失败: ${FAIL_COUNT}${NC}"
echo "总计: $((PASS_COUNT + FAIL_COUNT))"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！${NC}"
else
    echo -e "${RED}有 ${FAIL_COUNT} 个测试失败，请检查日志${NC}"
fi