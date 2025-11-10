#!/usr/bin/env sh
# Simple check: exit 1 if any known checkstyle report contains severity="error"
set -eu
REPORTS="target/checkstyle-result.xml target/site/checkstyle.xml target/checkstyle-result.xml"
FOUND=0
for FILE in $REPORTS; do
  if [ -f "$FILE" ]; then
    if grep -q 'severity="error"' "$FILE"; then
      echo "Found Checkstyle error severity entries in $FILE" >&2
      FOUND=1
    else
      echo "No Checkstyle error severity entries found in $FILE"
    fi
  else
    echo "Checkstyle report not found: $FILE"
  fi
done
if [ "$FOUND" -eq 1 ]; then
  exit 1
fi
exit 0
