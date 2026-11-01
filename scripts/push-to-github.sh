#!/usr/bin/env bash
# 把本地 main + v2.0.0 推到 GitHub，token 只用于本次推送，不写进 .git/config。
# 用法：GH_TOKEN=github_pat_xxx ./scripts/push-to-github.sh
set -euo pipefail

: "${GH_TOKEN:?需要 GH_TOKEN 环境变量（细粒度 PAT，需 Contents: Read and write）}"
REMOTE_URL="https://github.com/Yuxiximyxx/MagicFree4.git"
PUSH_URL="https://x-access-token:${GH_TOKEN}@github.com/Yuxiximyxx/MagicFree4.git"

git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REMOTE_URL"
git remote set-url origin "$REMOTE_URL"

echo "→ 推送 main"
git push "$PUSH_URL" main:main
echo "→ 推送 tag v2.0.0"
git push "$PUSH_URL" refs/tags/v2.0.0

echo "→ 校验远端 HEAD"
git ls-remote "$PUSH_URL" refs/heads/main
echo "完成。远端地址：$REMOTE_URL （origin 已保持为无 token 的干净地址）"
