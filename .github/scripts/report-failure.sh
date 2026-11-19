#!/usr/bin/env bash
# Turns Gradle output into GitHub annotations so build errors are readable
# without downloading the full (and very large) job log.
set -uo pipefail

LOG_DIR="${1:-build-logs}"
shopt -s nullglob
files=("$LOG_DIR"/*.log)
if [ ${#files[@]} -eq 0 ]; then
  echo "::error title=No logs::No build log files were produced in $LOG_DIR"
  exit 0
fi

for f in "${files[@]}"; do
  name="$(basename "$f" .log)"
  # Show the first real compilation/test failure lines.
  { grep -nE "^e: |^w: .*(deprecat|error)|FAILURE: Build failed|Execution failed for task|^> Task .* FAILED|error:|Caused by:|^\* What went wrong|^[0-9]+ tests? completed, .*failed" "$f" || true; } \
    | sed -e 's/::/#/g' \
    | head -25 \
    | while IFS= read -r line; do
        safe="${line//%/%25}"
        safe="${safe//$'\r'/}"
        safe="${safe:0:900}"
        echo "::error title=${name}::${safe}"
      done
done
