#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$project_root"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

forbidden_runtime='ClaudeCodeExecutor|CodexExecutor|OpenClawExecutor|CliProcessRunner|SandboxTranslator|ExecutorToggleService|WorkspaceManager|ExecutorRouter'
if rg -n "$forbidden_runtime" src/main/java frontend/src src/main/resources/application.example.yaml; then
  fail "unsafe executor or worktree runtime remains"
fi

if rg -n 'ProcessBuilder|Runtime\.getRuntime\(\)\.exec|git[[:space:]]+worktree' src/main/java/com/example/demo/dispatch; then
  fail "dispatch can still launch a local process or worktree"
fi

if rg -n 'org\.postgresql|jdbc:postgresql|(?:implementation|runtimeOnly).*postgresql' \
    build.gradle src/main/java src/main/resources; then
  fail "Java runtime still contains a PostgreSQL datasource or driver"
fi

if [[ -n "$(git status --porcelain -- '*.py' ':**/*.py')" ]]; then
  fail "Python source is modified; this change must remain Java/Vue-only"
fi

./gradlew compileJava compileTestJava --no-daemon --console=plain
./gradlew test --no-daemon --console=plain
(
  cd frontend
  npm run type-check
  npm run build
)

if [[ "${SKIP_DB_REHEARSAL:-0}" != "1" ]]; then
  "$project_root/scripts/rehearse-multi-user-migration.sh"
fi

echo "multi_user_release_gate=PASS"
