#!/usr/bin/env bash
set -euo pipefail
REMOTE="https://github.com/mrjohndowe/HiddenNoir.git"

if [ ! -d .git ]; then git init; fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi

echo "origin -> $(git remote get-url origin)"
