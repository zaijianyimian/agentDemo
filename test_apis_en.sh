#!/bin/bash

# API Incremental Test - English version (avoid encoding issues)
# Base URL: http://localhost:8000/api

BASE_URL="http://localhost:8000/api"
CONTENT_TYPE="Content-Type: application/json; charset=UTF-8"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0

test_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local expected_status="$5"

    echo -e "${BLUE}[Test] ${name}${NC}"
    echo "Request: ${method} ${url}"

    if [ "$method" == "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" -X GET "${url}" -H "Accept: application/json" 2>&1)
    elif [ "$method" == "POST" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST "${url}" -H "${CONTENT_TYPE}" -H "Accept: application/json" -d "${data}" 2>&1)
    elif [ "$method" == "PUT" ]; then
        response=$(curl -s -w "\n%{http_code}" -X PUT "${url}" -H "${CONTENT_TYPE}" -H "Accept: application/json" -d "${data}" 2>&1)
    elif [ "$method" == "DELETE" ]; then
        response=$(curl -s -w "\n%{http_code}" -X DELETE "${url}" -H "Accept: application/json" 2>&1)
    fi

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expected_status" ]; then
        echo -e "${GREEN}[PASS] HTTP ${http_code}${NC}"
        echo "Response: ${body:0:200}"
        ((PASS_COUNT++))
    else
        echo -e "${RED}[FAIL] HTTP ${http_code} (expected: ${expected_status})${NC}"
        echo "Response: ${body:0:200}"
        ((FAIL_COUNT++))
    fi
    echo "---"
}

echo "========================================"
echo "API Incremental Test (English Version)"
echo "========================================"
echo ""

# 1. Chat History API - Use URL params for @RequestParam
echo -e "${YELLOW}=== 1. Chat History API ===${NC}"
test_api "Create Session" "POST" "${BASE_URL}/chat/history/session?title=TestSession" "" "200"
test_api "Session List" "GET" "${BASE_URL}/chat/history/sessions" "" "200"
test_api "Update Title (URL param)" "PUT" "${BASE_URL}/chat/history/session/4/title?title=UpdatedTitle" "" "200"
test_api "Add Message (URL params)" "POST" "${BASE_URL}/chat/history/session/4/message?role=user&content=HelloWorld" "" "200"
test_api "Get Messages" "GET" "${BASE_URL}/chat/history/session/4/messages" "" "200"
echo ""

# 2. Memory API
echo -e "${YELLOW}=== 2. Memory API ===${NC}"
MEMORY_DATA='{"recentMessages":["User says: I like programming","Assistant replies: Good to know"],"userId":"test-user"}'
test_api "Memory Extract-Store" "POST" "${BASE_URL}/memory/extract-store" "${MEMORY_DATA}" "200"
test_api "Memory Search" "GET" "${BASE_URL}/memory/search?query=programming&userId=test-user" "" "200"
echo ""

# 3. Knowledge API
echo -e "${YELLOW}=== 3. Knowledge API ===${NC}"
KB_DATA='{"name":"TestKB","description":"Test knowledge base","type":"DOCUMENT"}'
test_api "Create Knowledge" "POST" "${BASE_URL}/knowledge" "${KB_DATA}" "200"
test_api "Knowledge List" "GET" "${BASE_URL}/knowledge/list" "" "200"
KB_UPDATE='{"name":"UpdatedKB","description":"Updated description"}'
test_api "Update Knowledge" "PUT" "${BASE_URL}/knowledge/1" "${KB_UPDATE}" "200"
test_api "Toggle Knowledge" "PUT" "${BASE_URL}/knowledge/1/toggle" "" "200"
echo ""

# 4. Task API
echo -e "${YELLOW}=== 4. Task API ===${NC}"
TASK_DATA='{"name":"TestTask","taskType":"CHAT","cronExpression":"0 0 12 * * ?","params":"Good morning","enabled":true}'
test_api "Create Task" "POST" "${BASE_URL}/task" "${TASK_DATA}" "200"
test_api "Task List" "GET" "${BASE_URL}/task/list" "" "200"
test_api "Task Detail" "GET" "${BASE_URL}/task/1" "" "200"
TASK_UPDATE='{"name":"UpdatedTask","taskType":"CHAT","cronExpression":"0 0 18 * * ?"}'
test_api "Update Task" "PUT" "${BASE_URL}/task/1" "${TASK_UPDATE}" "200"
test_api "Toggle Task" "PUT" "${BASE_URL}/task/1/toggle" "" "200"
echo ""

# 5. Model API
echo -e "${YELLOW}=== 5. Model API ===${NC}"
MODEL_DATA='{"name":"TestModel","provider":"openai","apiKey":"test-key","baseUrl":"https://api.openai.com/v1","modelName":"gpt-4","enabled":false}'
test_api "Create Model" "POST" "${BASE_URL}/model" "${MODEL_DATA}" "200"
test_api "Model List" "GET" "${BASE_URL}/model/list" "" "200"
test_api "Model Providers" "GET" "${BASE_URL}/model/providers" "" "200"
MODEL_UPDATE='{"name":"UpdatedModel","modelName":"gpt-4-turbo"}'
test_api "Update Model" "PUT" "${BASE_URL}/model/1" "${MODEL_UPDATE}" "200"
test_api "Toggle Model" "PUT" "${BASE_URL}/model/1/toggle" "" "200"
echo ""

# 6. Note API
echo -e "${YELLOW}=== 6. Note API ===${NC}"
NOTE_DATA='{"title":"TestNote","content":"This is a test note content"}'
test_api "Create Note" "POST" "${BASE_URL}/note" "${NOTE_DATA}" "200"
test_api "Note List" "GET" "${BASE_URL}/note/list" "" "200"
NOTE_UPDATE='{"title":"UpdatedNote","content":"Updated content"}'
test_api "Update Note" "PUT" "${BASE_URL}/note/2" "${NOTE_UPDATE}" "200"
test_api "Toggle Pin" "PUT" "${BASE_URL}/note/2/pin" "" "200"
test_api "Search Notes" "GET" "${BASE_URL}/note/search?keyword=test" "" "200"
echo ""

# 7. Code Snippet API
echo -e "${YELLOW}=== 7. Code Snippet API ===${NC}"
SNIPPET_DATA='{"title":"HelloWorld","language":"java","code":"public class Hello { public static void main(String[] args) { System.out.println(\"Hello\"); } }","description":"Java example"}'
test_api "Create Snippet" "POST" "${BASE_URL}/snippet" "${SNIPPET_DATA}" "200"
test_api "Snippet List" "GET" "${BASE_URL}/snippet/list" "" "200"
test_api "By Language" "GET" "${BASE_URL}/snippet/language/java" "" "200"
test_api "Search Snippet" "GET" "${BASE_URL}/snippet/search?keyword=Hello" "" "200"
echo ""

# 8. Schedule API
echo -e "${YELLOW}=== 8. Schedule API ===${NC}"
SCHEDULE_DATA='{"title":"TestSchedule","description":"Test description","startTime":"2026-03-29T10:00:00","endTime":"2026-03-29T12:00:00"}'
test_api "Create Schedule" "POST" "${BASE_URL}/schedule" "${SCHEDULE_DATA}" "200"
test_api "Latest Schedule" "GET" "${BASE_URL}/schedule/latest" "" "200"
test_api "Today Schedule" "GET" "${BASE_URL}/schedule/today" "" "200"
test_api "Tomorrow Schedule" "GET" "${BASE_URL}/schedule/tomorrow" "" "200"
test_api "Cancel Schedule" "PUT" "${BASE_URL}/schedule/1/cancel" "" "200"
echo ""

# 9. Skill API
echo -e "${YELLOW}=== 9. Skill API ===${NC}"
test_api "Enabled Skills" "GET" "${BASE_URL}/skill/enabled" "" "200"
test_api "Builtin Skills" "GET" "${BASE_URL}/skill/builtin" "" "200"
test_api "Skill Categories" "GET" "${BASE_URL}/skill/categories" "" "200"
test_api "Skill Detail" "GET" "${BASE_URL}/skill/1" "" "200"
echo ""

# 10. Email API
echo -e "${YELLOW}=== 10. Email API ===${NC}"
test_api "Enabled Emails" "GET" "${BASE_URL}/email/config/enabled" "" "200"
test_api "Email Templates" "GET" "${BASE_URL}/email/templates" "" "200"
test_api "Listener Status" "GET" "${BASE_URL}/email/listener/status" "" "200"
echo ""

# 11. Chat with Session
echo -e "${YELLOW}=== 11. Chat with Session ===${NC}"
test_api "Chat Complete Session" "GET" "${BASE_URL}/chat/complete/session?message=Hello&sessionId=4" "" "200"
test_api "Stream Test" "GET" "${BASE_URL}/chat/stream/test?message=TestStream" "" "200"
echo ""

# 12. File API
echo -e "${YELLOW}=== 12. File API ===${NC}"
test_api "File List" "GET" "${BASE_URL}/file/list" "" "200"
test_api "Search Files" "GET" "${BASE_URL}/file/search?importance=HIGH" "" "200"
echo ""

# Summary
echo "========================================"
echo -e "${YELLOW}Test Summary${NC}"
echo "========================================"
echo -e "${GREEN}Passed: ${PASS_COUNT}${NC}"
echo -e "${RED}Failed: ${FAIL_COUNT}${NC}"
echo "Total: $((PASS_COUNT + FAIL_COUNT))"

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
else
    echo -e "${RED}${FAIL_COUNT} tests failed${NC}"
fi