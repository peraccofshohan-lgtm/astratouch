#!/usr/bin/env bash
# Turns Gradle output into GitHub annotations so build/test errors are readable
# without downloading the full job log.
set -uo pipefail

LOG_DIR="${1:-build-logs}"
shopt -s nullglob
files=("$LOG_DIR"/*.log)
if [ ${#files[@]} -eq 0 ]; then
  echo "::error title=No logs::No build log files were produced in $LOG_DIR"
  exit 0
fi

emit() {
  local name="$1" line="$2"
  local safe="${line//%/%25}"
  safe="${safe//$'\r'/}"
  safe="${safe:0:900}"
  echo "::error title=${name}::${safe}"
}

for f in "${files[@]}"; do
  name="$(basename "$f" .log)"

  # 1. Priority: real compilation errors.
  grep -E "^e: " "$f" | head -12 | while IFS= read -r line; do emit "$name" "$line"; done

  # 2. Test failures: failing test names, assertion output and the summary line.
  grep -E "FAILED$|^[A-Za-z0-9_.]+ > .* FAILED|tests? completed, .*failed|AssertionError|expected:<|expected:|java\.lang\.|No tests found|Test.*failed" "$f" \
    | grep -v "^w: " \
    | head -15 | while IFS= read -r line; do emit "$name" "$line"; done

  # 3. Gradle task-level failures.
  grep -E "Execution failed for task|What went wrong|> Task .* FAILED|Caused by:" "$f" \
    | head -8 | while IFS= read -r line; do emit "$name" "$line"; done

  # 4. Fallback: tail of the log when nothing matched above.
  if ! grep -qE "^e: |FAILED|What went wrong|error:" "$f"; then
    tail -n 12 "$f" | while IFS= read -r line; do emit "$name" "$line"; done
  fi
done
