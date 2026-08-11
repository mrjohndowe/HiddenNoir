#!/usr/bin/env bash
set -euo pipefail
echo "Scanning for obvious sensitive logging patterns..."
if grep -RInE '(conversation|Authorization|refresh token).*(log|println|print)'   api/src app-android/app/src 2>/dev/null; then
  echo "Potential sensitive logging found. Review required."
  exit 1
fi
echo "No obvious sensitive logging patterns found."
