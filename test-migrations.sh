#!/usr/bin/env bash
# 迁移脚本验证：全部在独立的 migtest_* 测试库上进行。
# 不触碰 agent 库，不删除任何现有卷或数据。测试库在结束时统一 DROP。
set -u
cd "$(dirname "$0")"

MIG=../emailagent/migrations
PSQL_BASE=(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U agent)
pass=0; fail=0

run_migrations() {   # $1 = database
  local db=$1 f
  for f in 20260913_add_email_user_scope 20260913_add_email_event_id 20260929_add_chat_session_message; do
    "${PSQL_BASE[@]}" -d "$db" -f - < "$MIG/$f.sql" >/dev/null 2>&1 || {
      echo "  !! $f failed on $db"; "${PSQL_BASE[@]}" -d "$db" -f - < "$MIG/$f.sql" 2>&1 | tail -5; return 1; }
  done
}
q() { "${PSQL_BASE[@]}" -d "$1" -tAc "$2"; }
check() { # $1=label $2=actual $3=expected
  if [ "$2" = "$3" ]; then echo "  PASS $1 ($2)"; pass=$((pass+1));
  else echo "  FAIL $1: got '$2' want '$3'"; fail=$((fail+1)); fi
}
fresh_db() { "${PSQL_BASE[@]}" -d agent -c "DROP DATABASE IF EXISTS $1" -c "CREATE DATABASE $1" >/dev/null; }

echo "=== T1 空库首次初始化 ==="
fresh_db migtest_fresh
run_migrations migtest_fresh || fail=$((fail+1))
check "email_message 建表" "$(q migtest_fresh "select count(*) from information_schema.tables where table_name='email_message'")" 1
check "email_attachment 建表" "$(q migtest_fresh "select count(*) from information_schema.tables where table_name='email_attachment'")" 1
check "schedule 建表" "$(q migtest_fresh "select count(*) from information_schema.tables where table_name='schedule'")" 1
check "chat_session 建表" "$(q migtest_fresh "select count(*) from information_schema.tables where table_name='chat_session'")" 1
check "email_message.event_id 列" "$(q migtest_fresh "select count(*) from information_schema.columns where table_name='email_message' and column_name='event_id'")" 1
check "user_id 为 NOT NULL" "$(q migtest_fresh "select is_nullable from information_schema.columns where table_name='email_message' and column_name='user_id'")" NO

echo "=== T2 空库重复执行（幂等）==="
run_migrations migtest_fresh || fail=$((fail+1))
run_migrations migtest_fresh || fail=$((fail+1))
check "email_message 仍只有 1 张" "$(q migtest_fresh "select count(*) from information_schema.tables where table_name='email_message'")" 1
check "索引无重复" "$(q migtest_fresh "select count(*) from pg_indexes where indexname='idx_email_message_user_id'")" 1

echo "=== T3 已有库升级：数据保留 ==="
fresh_db migtest_upgrade
run_migrations migtest_upgrade
q migtest_upgrade "insert into email_message(user_id,sender,subject,status) values (7,'a@x.test','keepme','COMPLETED')" >/dev/null
q migtest_upgrade "insert into schedule(user_id,title,start_time,end_time) values (7,'keep-sched',now(),now()+interval '1 hour')" >/dev/null
q migtest_upgrade "insert into chat_session(user_id,title) values (7,'keep-chat')" >/dev/null
run_migrations migtest_upgrade || fail=$((fail+1))
check "email 记录保留" "$(q migtest_upgrade "select subject from email_message where user_id=7")" keepme
check "schedule 记录保留" "$(q migtest_upgrade "select title from schedule where user_id=7")" keep-sched
check "chat_session 记录保留" "$(q migtest_upgrade "select title from chat_session where user_id=7")" keep-chat
check "email 记录数仍为 1" "$(q migtest_upgrade "select count(*) from email_message")" 1

echo "=== T4 单用户旧结构 + 有数据：必须中止且不删数据 ==="
fresh_db migtest_legacy
q migtest_legacy "create table email_message(email_id bigserial primary key, sender varchar(512) not null, subject text)" >/dev/null
q migtest_legacy "insert into email_message(sender,subject) values ('legacy@x.test','legacy-subject')" >/dev/null
out=$("${PSQL_BASE[@]}" -d migtest_legacy -f - < "$MIG/20260913_add_email_user_scope.sql" 2>&1)
if echo "$out" | grep -q 'ERROR'; then echo "  PASS 中止（未静默通过）"; pass=$((pass+1));
else echo "  FAIL: 脚本未中止，输出: $out"; fail=$((fail+1)); fi
check "旧数据仍在" "$(q migtest_legacy "select subject from email_message where sender='legacy@x.test'")" legacy-subject
check "user_id 未被猜测写入" "$(q migtest_legacy "select count(*) from information_schema.columns where table_name='email_message' and column_name='user_id'")" 0

echo "=== T5 单用户旧结构 + 空表：补列后完成 ==="
fresh_db migtest_legacy_empty
q migtest_legacy_empty "create table email_message(email_id bigserial primary key, sender varchar(512) not null)" >/dev/null
run_migrations migtest_legacy_empty || fail=$((fail+1))
check "补齐 user_id" "$(q migtest_legacy_empty "select count(*) from information_schema.columns where table_name='email_message' and column_name='user_id'")" 1
check "user_id 收紧为 NOT NULL" "$(q migtest_legacy_empty "select is_nullable from information_schema.columns where table_name='email_message' and column_name='user_id'")" NO
q migtest_legacy_empty "insert into email_message(user_id,sender) values (9,'new@x.test')" >/dev/null
check "可写入新记录" "$(q migtest_legacy_empty "select sender from email_message where user_id=9")" new@x.test

echo "=== 清理测试库（不动 agent 库与其数据）==="
for db in migtest_fresh migtest_upgrade migtest_legacy migtest_legacy_empty; do
  "${PSQL_BASE[@]}" -d agent -c "DROP DATABASE IF EXISTS $db" >/dev/null
done

echo
echo "结果: PASS=$pass FAIL=$fail"
[ "$fail" -eq 0 ]
