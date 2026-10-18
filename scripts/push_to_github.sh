#!/usr/bin/env bash
# প্রজেক্টটি GitHub-এ আপলোড করে — আপলোড হলেই GitHub Actions নিজে থেকে APK বানাবে।
#   gh auth login      (একবারই)
#   ./scripts/push_to_github.sh
set -euo pipefail
cd "$(dirname "$0")/.."

command -v gh >/dev/null || { echo "GitHub CLI (gh) ইনস্টল করুন: https://cli.github.com" >&2; exit 1; }
command -v git >/dev/null || { echo "git ইনস্টল করুন" >&2; exit 1; }

[ -d .git ] || git init -b main
git add -A
git commit -m "BestCut: initial commit" || true

if ! git remote get-url origin >/dev/null 2>&1; then
  gh repo create bestcut --private --source=. --remote=origin --push
else
  git push -u origin main
fi

echo
echo "পুশ হয়ে গেছে। এখন GitHub → Actions ট্যাবে বিল্ড চলবে,"
echo "শেষ হলে Releases (latest) অথবা Actions → Artifacts থেকে BestCut.apk নামান।"
