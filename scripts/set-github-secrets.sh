#!/usr/bin/env bash
# 将固定签名证书写入 GitHub Actions Secrets（只执行一次）
set -euo pipefail
REPO="${REPO:-468336329Zc/renovationMiniProgram}"
KEYSTORE="${1:-android-admin/keystore/demo-release.jks}"

if [ ! -f "$KEYSTORE" ]; then
  echo "找不到 keystore: $KEYSTORE" >&2
  exit 1
fi
if ! command -v gh >/dev/null 2>&1; then
  echo "未安装 GitHub CLI（gh），请先安装并执行 gh auth login" >&2
  exit 1
fi
gh auth status >/dev/null

gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO" --body "$(base64 -w 0 "$KEYSTORE")"
gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO" --body "renovation-demo"
gh secret set ANDROID_KEY_ALIAS --repo "$REPO" --body "renovation"
gh secret set ANDROID_KEY_PASSWORD --repo "$REPO" --body "${ANDROID_KEY_PASSWORD:?请先设置环境变量 ANDROID_KEY_PASSWORD}"

echo "已写入 4 个签名 Secrets：$REPO"
echo "证书指纹：$(keytool -list -v -keystore "$KEYSTORE" -storepass renovation-demo 2>/dev/null | awk '/SHA256:/{print $2; exit}')"
