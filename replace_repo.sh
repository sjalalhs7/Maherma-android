#!/usr/bin/env bash
# Replaces the ENTIRE contents of a GitHub repo with this project (old files removed, new files added).
# Usage: ./tools/replace_repo.sh https://github.com/USER/REPO.git [branch]
set -euo pipefail
REPO="${1:?Usage: replace_repo.sh <repo-url> [branch]}"
BRANCH="${2:-main}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone "$REPO" "$TMP/repo"
cd "$TMP/repo"
git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH"
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +       # remove everything old
tar -C "$SRC" --exclude=.git --exclude=build --exclude=.gradle --exclude=local.properties -cf - . | tar -xf -
git add -A
if git diff --cached --quiet; then echo "Nothing changed."; exit 0; fi
git commit -m "Replace with latest Maherma build"
git push -u origin "$BRANCH"
echo "Done: repo now matches this folder exactly."
