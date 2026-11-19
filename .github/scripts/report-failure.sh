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

  # 2b. Android Lint errors look like:
  #     /path/File.kt:12: Error: message [IssueId]
  grep -E "Error: .*\[[A-Za-z]+\]$|^[^ ]+\.(kt|xml|java):[0-9]+: (Error|Warning):" "$f" \
    | head -25 | while IFS= read -r line; do emit "$name" "$line"; done

  # 3. Gradle task-level failures, with the lines that follow the reason.
  grep -A 12 -E "What went wrong" "$f" | head -20 | while IFS= read -r line; do emit "$name" "$line"; done
  grep -E "Execution failed for task|> Task .* FAILED|Caused by:" "$f" \
    | head -6 | while IFS= read -r line; do emit "$name" "$line"; done

  # 4. Always attach the tail so nothing is invisible when the patterns miss.
  tail -n 25 "$f" | while IFS= read -r line; do emit "$name" "$line"; done
done
