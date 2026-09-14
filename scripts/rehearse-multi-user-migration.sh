#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
schema_file="$project_root/src/main/resources/sql/schema_init.sql"
migration_file="$project_root/src/main/resources/sql/migrant.sql"
mysql_image=${MYSQL_IMAGE:-mysql:latest}
container_name="agentdemo-mysql-rehearsal-$$"
mysql_password="agentdemo-rehearsal-only"
scratch_dir=$(mktemp -d)

cleanup() {
  docker stop "$container_name" >/dev/null 2>&1 || true
  rm -rf "$scratch_dir"
}
trap cleanup EXIT

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v docker >/dev/null || fail "docker is required"
command -v sha256sum >/dev/null || fail "sha256sum is required"

docker run -d --rm --name "$container_name" \
  -e MYSQL_ROOT_PASSWORD="$mysql_password" "$mysql_image" >/dev/null

for attempt in $(seq 1 60); do
  if docker exec "$container_name" mysql -uroot "-p$mysql_password" \
      -N -B -e "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 60 ]]; then
    fail "temporary MySQL did not become ready"
  fi
  sleep 1
done

mysql_root() {
  docker exec "$container_name" mysql -uroot "-p$mysql_password" "$@"
}

mysql_root -e "CREATE DATABASE schema_rehearsal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
docker exec -i "$container_name" mysql -uroot "-p$mysql_password" schema_rehearsal < "$schema_file"
docker exec -i "$container_name" mysql -uroot "-p$mysql_password" schema_rehearsal < "$schema_file"

schema_metrics=$(mysql_root -N -B -e "
SELECT CONCAT(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='schema_rehearsal'), ',',
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='schema_rehearsal' AND column_name='user_id'), ',',
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='schema_rehearsal' AND constraint_type='FOREIGN KEY')
);")
schema_fingerprint=$(mysql_root -N -B -e "
SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>')
FROM information_schema.columns
WHERE table_schema='schema_rehearsal'
ORDER BY table_name,ordinal_position;" | sha256sum | awk '{print $1}')

legacy_schema="$scratch_dir/legacy-schema.sql"
awk '/15\. 多用户与当前运行时结构追加/ {exit} {print}' "$schema_file" > "$legacy_schema"
mysql_root -e "CREATE DATABASE legacy_rehearsal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
docker exec -i "$container_name" mysql -uroot "-p$mysql_password" legacy_rehearsal < "$legacy_schema"

mysql_root legacy_rehearsal -e "
INSERT INTO user_account(id,username,email,password_hash,enabled) VALUES
  (7,'legacy-owner','legacy@example.test','fixture',1),
  (8,'other-user','other@example.test','fixture',1);
INSERT INTO email_config(id,email,host,enabled) VALUES (11,'mail@example.test','imap.example.test',1);
INSERT INTO email_listener_state(id,config_id,provider,listen_mode) VALUES (21,11,'GENERIC_IMAP','POLLING');
INSERT INTO chat_session(id,title) VALUES (31,'legacy');
INSERT INTO chat_message(id,session_id,role,content) VALUES (41,31,'user','hello');
INSERT INTO schedule_event(id,title,event_date) VALUES (51,'meeting','2026-09-13');
INSERT INTO scheduled_task(id,name,task_type,cron_expression) VALUES (61,'fixture','REMINDER','0 0 * * * ?');
INSERT INTO document(id,file_name,status) VALUES (81,'fixture.txt','pending');
INSERT INTO dispatched_task(id,email_id,executor,execution_instruction,executor_timeout_seconds)
  VALUES (91,11,'isolated-worker','do work',60);
INSERT INTO push_config(id,user_id,push_email) VALUES (101,7,'owner@example.test');
"

if docker exec -i "$container_name" mysql -uroot "-p$mysql_password" legacy_rehearsal \
    < "$migration_file" > "$scratch_dir/missing-owner.log" 2>&1; then
  fail "migration unexpectedly accepted a missing LEGACY_OWNER_USER_ID"
fi

preflight_owner_columns=$(mysql_root -N -B legacy_rehearsal -e "
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema='legacy_rehearsal' AND table_name='email_config' AND column_name='user_id';")
[[ "$preflight_owner_columns" == "0" ]] || fail "missing-owner preflight changed email_config ownership"

for run in 1 2; do
  sed '1i SET @LEGACY_OWNER_USER_ID := 7;' "$migration_file" |
    docker exec -i "$container_name" mysql -uroot "-p$mysql_password" legacy_rehearsal >/dev/null
done

invalid_owned_rows=$(mysql_root -N -B legacy_rehearsal -e "
SELECT
  (SELECT COUNT(*) FROM email_config WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM email_listener_state WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM chat_session WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM chat_message WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM schedule_event WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM scheduled_task WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM document WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM dispatched_task WHERE user_id IS NULL OR user_id<>7) +
  (SELECT COUNT(*) FROM push_config WHERE user_id IS NULL OR user_id<>7);")
[[ "$invalid_owned_rows" == "0" ]] || fail "legacy fixture contains invalid owners after migration"

journal_summary=$(mysql_root -N -B legacy_rehearsal -e "
SELECT CONCAT(COUNT(*), ',', MIN(attempt_count), ',', MAX(attempt_count), ',',
  SUM(status='COMPLETED'), ',', COUNT(DISTINCT owner_user_id))
FROM migration_journal;")
[[ "$journal_summary" == "4,2,2,4,1" ]] || fail "unexpected migration journal summary: $journal_summary"

legacy_fingerprint=$(mysql_root -N -B -e "
SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>')
FROM information_schema.columns
WHERE table_schema='legacy_rehearsal'
ORDER BY table_name,ordinal_position;" | sha256sum | awk '{print $1}')

echo "schema_metrics=tables,owner_columns,foreign_keys:$schema_metrics"
echo "schema_checksum=$schema_fingerprint"
echo "legacy_owner_user_id=7"
echo "legacy_journal=steps,min_attempts,max_attempts,completed,owners:$journal_summary"
echo "legacy_invalid_owned_rows=$invalid_owned_rows"
echo "legacy_checksum=$legacy_fingerprint"
echo "migration_rehearsal=PASS"
