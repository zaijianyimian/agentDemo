#!/usr/bin/env bash
# 端到端验收：全部经浏览器唯一入口 http://127.0.0.1:3000。
# 不打印令牌与邮件正文；数据库只按计数与状态断言。
set -u
cd "$(dirname "$0")"
BASE=http://127.0.0.1:3000
STAMP=$(date +%s)
UA="acct_a_$STAMP"; UB="acct_b_$STAMP"
EMAIL_A="acct_a_$STAMP@example.test"; EMAIL_B="acct_b_$STAMP@example.test"
PASS='Passw0rd!Aa1'
pass=0; fail=0
ok()   { echo "  PASS $1"; pass=$((pass+1)); }
no()   { echo "  FAIL $1"; fail=$((fail+1)); }
chk()  { if [ "$2" = "$3" ]; then ok "$1 ($2)"; else no "$1: got '$2' want '$3'"; fi; }
# jq_ <点分路径>：从 stdin 的 JSON 里取值，如 jq_ data.accessToken
jq_()  { python3 -c "
import sys, json
d = json.load(sys.stdin)
for k in '$1'.split('.'):
    d = d[k]
print('' if d is None else d)
" 2>/dev/null; }
code() { curl -s -m 30 -o /dev/null -w '%{http_code}' "$@"; }

echo "=== E1 注册/登录/内省 ==="
MYSQLPW=$(grep '^MYSQL_PASSWORD=' .env | cut -d= -f2-)
# --default-character-set=utf8mb4：不加会把中文标题读成 ????，导致后续比对失败
sqlq() { docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p"$MYSQLPW" -N -B -e "$1" 2>/dev/null; }
TA=""; TB=""; UA_ID=""; UB_ID=""
for u in "$UA:$EMAIL_A" "$UB:$EMAIL_B"; do
  n=${u%%:*}; e=${u#*:}
  r=$(curl -s -m 30 -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
      -d "{\"username\":\"$n\",\"password\":\"$PASS\",\"email\":\"$e\"}")
  [ "$(echo "$r" | jq_ success)" = "True" ] && ok "$n 注册" || no "$n 注册失败"
  # 联调环境没有可用收件箱：直接取回本服务自己写入的注册验证码完成邮箱确认。
  # 这是测试账号的开户步骤，不改动产品代码。
  curl -s -m 30 -X POST $BASE/api/auth/login/email/send-code -H 'Content-Type: application/json' \
      -d "{\"email\":\"$e\"}" >/dev/null
  vcode=$(sqlq "select code from agent.auth_email_code where email='$e' and purpose='REGISTER' and used=0 order by id desc limit 1")
  if [ -z "$vcode" ]; then no "$n 未取到注册验证码"; continue; fi
  v=$(curl -s -m 30 -X POST $BASE/api/auth/login/email -H 'Content-Type: application/json' \
      -d "{\"email\":\"$e\",\"code\":\"$vcode\"}")
  [ "$(echo "$v" | jq_ success)" = "True" ] && ok "$n 邮箱验证码确认" || no "$n 邮箱确认失败"
  t=$(curl -s -m 30 -X POST $BASE/api/auth/login/password -H 'Content-Type: application/json' \
      -d "{\"username\":\"$n\",\"password\":\"$PASS\"}")
  tok=$(echo "$t" | jq_ data.accessToken)
  [ -n "$tok" ] && ok "$n 密码登录取得令牌" || { no "$n 登录失败: $(echo "$t" | head -c 200)"; continue; }
  if [ "$n" = "$UA" ]; then TA=$tok; else TB=$tok; fi
  i=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X POST $BASE/api/auth/introspect -H "Authorization: Bearer $tok")
  chk "$n 内省" "$i" 200
  uid=$(curl -s -m 30 -X POST $BASE/api/auth/introspect -H "Authorization: Bearer $tok" | jq_ user_id)
  [ -n "$uid" ] && ok "$n 内省返回 userId=$uid" || no "$n 内省无 userId"
  if [ "$n" = "$UA" ]; then UA_ID=$uid; else UB_ID=$uid; fi
done
[ -n "$TA" ] && [ -n "$TB" ] || { echo "缺少测试账号令牌，后续用例跳过"; exit 1; }

echo "=== E2 无效令牌被拒绝 ==="
chk "无效令牌 /ai/chat/sessions" "$(code $BASE/ai/chat/sessions -H 'Authorization: Bearer invalid-token')" 401
chk "无效令牌 /ai/email/list" "$(code $BASE/ai/email/list -H 'Authorization: Bearer invalid-token')" 401
chk "无令牌 /ai/chat/sessions" "$(code $BASE/ai/chat/sessions)" 401

echo "=== E3 /ai/internal/* 不可经浏览器入口访问 ==="
chk "/ai/internal/chat/complete" "$(code -X POST $BASE/ai/internal/chat/complete -H 'Content-Type: application/json' -d '{}')" 404
chk "/ai/internal/chat/stream" "$(code -X POST $BASE/ai/internal/chat/stream -H 'Content-Type: application/json' -d '{}')" 404

echo "=== E4 服务令牌缺失/错误时拒绝（两侧）==="
# Python 侧：经 nginx 已被端口/路径隔离；直连容器网络验证令牌校验本身
py_no=$(docker compose exec -T graph python -c "
import urllib.request,urllib.error
try:
    r=urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8001/internal/chat/complete',data=b'{}',headers={'Content-Type':'application/json'}))
    print(r.status)
except urllib.error.HTTPError as e: print(e.code)
" 2>/dev/null)
chk "Python /internal 无令牌" "$py_no" 401
py_bad=$(docker compose exec -T graph python -c "
import urllib.request,urllib.error
req=urllib.request.Request('http://127.0.0.1:8001/internal/chat/complete',data=b'{}',headers={'Content-Type':'application/json','X-Internal-Token':'wrong-token'})
try:
    r=urllib.request.urlopen(req); print(r.status)
except urllib.error.HTTPError as e: print(e.code)
" 2>/dev/null)
chk "Python /internal 错误令牌" "$py_bad" 401
# Java 侧：经 nginx 从宿主机调用 /api/internal/schedule
chk "Java /api/internal 无令牌" "$(code -X POST $BASE/api/internal/schedule -H 'Content-Type: application/json' -d '{"userId":1,"title":"x","eventTime":"2026-10-01T10:00:00"}')" 401
chk "Java /api/internal 错误令牌" "$(code -X POST $BASE/api/internal/schedule -H 'Content-Type: application/json' -H 'X-Internal-Token: wrong-token' -d '{"userId":1,"title":"x","eventTime":"2026-10-01T10:00:00"}')" 401

echo "=== E5 创建会话 + SSE 逐块到达 + [DONE] + 历史可读 ==="
SID=$(curl -s -m 30 -X POST $BASE/ai/chat/sessions -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{}' | jq_ session_id)
[ -n "$SID" ] && ok "创建会话 $SID" || { no "创建会话失败"; SID=""; }
if [ -n "$SID" ]; then
  # 记录首字节到达时刻与总块数，验证不是一次性聚合
  sse_out=$(curl -sN -m 120 -X POST $BASE/ai/chat/turn/stream \
      -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' \
      -d "{\"session_id\":\"$SID\",\"message\":\"用一句话介绍你自己\"}" \
      --no-buffer -w '\n__HTTP__%{http_code}' 2>/dev/null)
  http=$(echo "$sse_out" | tail -1 | sed 's/__HTTP__//')
  body=$(echo "$sse_out" | sed '$d')
  chk "SSE HTTP" "$http" 200
  frames=$(echo "$body" | grep -c '^data: ')
  if [ "$frames" -ge 2 ]; then ok "SSE 分块数=$frames（>1，逐块到达）"; else no "SSE 只收到 $frames 帧，可能被聚合"; fi
  echo "$body" | grep -q '^data: \[DONE\]$' && ok "收到 [DONE]" || no "未收到 [DONE]"
  hist=$(curl -s -m 30 $BASE/ai/chat/sessions/$SID/messages -H "Authorization: Bearer $TA")
  n=$(echo "$hist" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null)
  chk "历史消息条数（user + assistant）" "$n" 2
  done_n=$(echo "$hist" | python3 -c "
import sys,json;d=json.load(sys.stdin);print(sum(1 for m in d if m['status']=='COMPLETED'))" 2>/dev/null)
  chk "历史中 COMPLETED 消息数" "$done_n" 2
fi

echo "=== E6 取消流式请求：释放占用 + 半截回复不入库 ==="
# 本用例要求回复还在持续生成，因此把桩服务的分块间隔调大：
# 31 个字符 × 0.5s ≈ 15s，足够在中途断开。跑完立即恢复默认。
# 注意 mock-llm 每次启动都会 pip install，起来得很慢，必须等健康检查而不是 sleep。
MOCK_LLM_CHUNK_DELAY=0.5 docker compose up -d --force-recreate mock-llm >/dev/null 2>&1
wait_mock() {
  for _ in $(seq 1 60); do
    if docker compose exec -T graph python -c "
import urllib.request,sys
try:
    sys.exit(0 if urllib.request.urlopen('http://mock-llm:9000/healthz',timeout=3).status==200 else 1)
except Exception: sys.exit(1)" 2>/dev/null; then return 0; fi
    sleep 2
  done
  return 1
}
if wait_mock; then ok "桩服务就绪（已放慢到 0.5s/块）"; else no "桩服务未就绪"; fi
SID2=$(curl -s -m 30 -X POST $BASE/ai/chat/sessions -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{}' | jq_ session_id)
# 后台发起流式请求，3 秒后强制断开（此时仍在生成中）
( curl -sN -m 120 -X POST $BASE/ai/chat/turn/stream -H "Authorization: Bearer $TA" \
    -H 'Content-Type: application/json' -d "{\"session_id\":\"$SID2\",\"message\":\"请介绍一下你自己\"}" \
    --no-buffer >/dev/null 2>&1 ) &
BGPID=$!
sleep 3
# 生成中并发提交同一会话，验证占用确实存在（期望 409）
busy=$(code -m 20 -X POST $BASE/ai/chat/turn/stream -H "Authorization: Bearer $TA" \
    -H 'Content-Type: application/json' -d "{\"session_id\":\"$SID2\",\"message\":\"抢占\"}")
chk "生成中同一会话被拒（409）" "$busy" 409
kill $BGPID 2>/dev/null; wait $BGPID 2>/dev/null
sleep 4
# 取消后占用应已释放：同一会话可以再次提交
after=$(code -m 90 -X POST $BASE/ai/chat/turn -H "Authorization: Bearer $TA" \
    -H 'Content-Type: application/json' -d "{\"session_id\":\"$SID2\",\"message\":\"取消后继续\"}")
chk "取消后会话可继续（释放占用）" "$after" 200
hist2=$(curl -s -m 30 $BASE/ai/chat/sessions/$SID2/messages -H "Authorization: Bearer $TA")
# 被取消那一轮只留下用户消息，抢占被拒不写库，续聊补上 user + assistant：
# 角色序列 u（被取消）u（续聊）a（续聊），助手消息只有 1 条。
roles=$(echo "$hist2" | python3 -c "
import sys,json;d=json.load(sys.stdin)
print(''.join(m['role'][0] for m in d))" 2>/dev/null)
chk "取消后消息角色序列（u=用户 a=助手）" "$roles" "uua"
asst=$(echo "$hist2" | python3 -c "
import sys,json;d=json.load(sys.stdin)
print(sum(1 for m in d if m['role']=='assistant'))" 2>/dev/null)
chk "助手消息数（半截不入库）" "$asst" 1
# 恢复默认分块间隔，供后续用例使用
docker compose up -d --force-recreate mock-llm >/dev/null 2>&1
wait_mock && ok "桩服务已恢复默认速度" || no "桩服务未恢复"

echo "=== E7 用户 A/B 数据隔离 ==="
# 聊天：B 读不到 A 的会话
chk "B 读 A 的会话" "$(code $BASE/ai/chat/sessions/$SID -H "Authorization: Bearer $TB")" 404
# 消息接口按 user_id + session_id 过滤，B 拿到的是空列表而非 A 的内容
bmsg=$(curl -s -m 30 $BASE/ai/chat/sessions/$SID/messages -H "Authorization: Bearer $TB")
bn=$(echo "$bmsg" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null)
chk "B 读 A 的消息得到条数" "$bn" 0
# B 的会话列表不含 A 的会话
nA=$(curl -s -m 30 $BASE/ai/chat/sessions -H "Authorization: Bearer $TB" | python3 -c "
import sys,json;d=json.load(sys.stdin);print(sum(1 for s in d if s['session_id']=='$SID'))" 2>/dev/null)
chk "B 的会话列表不含 A 的会话" "$nA" 0
# 邮件分析
mailA=$(curl -s -m 30 $BASE/ai/email/list -H "Authorization: Bearer $TA")
mailB=$(curl -s -m 30 $BASE/ai/email/list -H "Authorization: Bearer $TB")
nA=$(echo "$mailA" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
nB=$(echo "$mailB" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
if [ "$nA" -gt 0 ]; then ok "A 能看到自己的邮件（$nA 封）"; else echo "  INFO A 当前无邮件分析记录"; fi
chk "B 看不到 A 的邮件" "$nB" 0

echo "=== E8 Agent 调用 create_schedule → Java/MySQL → 现有日程 API ==="
SID3=$(curl -s -m 30 -X POST $BASE/ai/chat/sessions -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{}' | jq_ session_id)
SCH_BEFORE=$(sqlq "select count(*) from agent.schedule_event where user_id=$UA_ID")
msg="请帮我在日程里添加一个测试日程，标题是 验收测试日程 $STAMP，开始时间 2026-10-05T10:00:00+08:00"
curl -sN -m 180 -X POST $BASE/ai/chat/turn/stream -H "Authorization: Bearer $TA" \
    -H 'Content-Type: application/json' -d "{\"session_id\":\"$SID3\",\"message\":\"$msg\"}" \
    --no-buffer >/dev/null 2>&1
sleep 2
SCH_AFTER=$(sqlq "select count(*) from agent.schedule_event where user_id=$UA_ID")
if [ "${SCH_AFTER:-0}" -gt "${SCH_BEFORE:-0}" ]; then
  ok "MySQL schedule_event 新增（$SCH_BEFORE -> $SCH_AFTER）"
else no "MySQL schedule_event 未新增（$SCH_BEFORE -> $SCH_AFTER）"; fi
# 桩服务用固定的标题与时间，这里按实际落库的那一行来查，而不是猜 Agent 会用什么标题
SCH_TITLE=$(sqlq "select title from agent.schedule_event where user_id=$UA_ID order by id desc limit 1")
SCH_DATE=$(sqlq "select event_date from agent.schedule_event where user_id=$UA_ID order by id desc limit 1")
if [ -n "$SCH_TITLE" ]; then ok "落库日程: 日期=$SCH_DATE 标题=$SCH_TITLE"; else no "读不到落库日程"; fi
# 现有日程 API 能查到（用 Java 既有的 /api/schedule/range，不新增任何接口）
api_hit=$(curl -s -m 30 "$BASE/api/schedule/range?startDate=$SCH_DATE&endDate=$SCH_DATE" -H "Authorization: Bearer $TA")
if [ -n "$SCH_TITLE" ] && echo "$api_hit" | grep -qF "$SCH_TITLE"; then
  ok "现有日程 API 查到 Agent 创建的日程"
else no "现有日程 API 未查到（日程 API $api_hit）"; fi
# B 读不到 A 的日程
apiB=$(curl -s -m 30 "$BASE/api/schedule/range?startDate=$SCH_DATE&endDate=$SCH_DATE" -H "Authorization: Bearer $TB")
if echo "$apiB" | grep -qF "$SCH_TITLE"; then no "B 能读到 A 的日程（越权）"; else ok "B 读不到 A 的日程"; fi

echo "=== E9 Java ApiResponse.success 判定 ==="
TOK=$(grep '^GRAPH_INTERNAL_TOKEN=' .env | cut -d= -f2-)
# 业务失败：userId 非法 -> Java 返回 HTTP 200 + success=false
biz=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X POST $BASE/api/internal/schedule \
  -H 'Content-Type: application/json' -H "X-Internal-Token: $TOK" \
  -d '{"userId":0,"title":"x","eventTime":"2026-10-01T10:00:00"}')
bizbody=$(curl -s -m 30 -X POST $BASE/api/internal/schedule -H 'Content-Type: application/json' \
  -H "X-Internal-Token: $TOK" -d '{"userId":0,"title":"x","eventTime":"2026-10-01T10:00:00"}')
chk "Java 业务失败仍是 HTTP 200" "$biz" 200
echo "$bizbody" | grep -q '"success":false' && ok "Java 业务失败 success=false（200 内）" || no "未观察到 success=false"
# 不存在的用户
nz=$(curl -s -m 30 -X POST $BASE/api/internal/schedule -H 'Content-Type: application/json' \
  -H "X-Internal-Token: $TOK" -d '{"userId":99999999,"title":"x","eventTime":"2026-10-01T10:00:00"}')
echo "$nz" | grep -q '"success":false' && ok "不存在用户 -> success=false" || echo "  INFO: $(echo "$nz" | head -c 160)"

echo
echo "结果: PASS=$pass FAIL=$fail"
echo "测试账号: $UA / $UB (userId $UA_ID / $UB_ID)"
[ "$fail" -eq 0 ]
