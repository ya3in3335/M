#!/usr/bin/env bash
# MED-K — تحديث الخادم (Cloudflare) وقواعد الحماية بعد تحديث الكود.
# الاستعمال في Cloud Shell:
#   CF_TOKEN=... OWNER_EMAILS=you@mail.com bash update-server.sh [PROJECT_ID]
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }

P="${1:-$(gcloud config get-value project 2>/dev/null || true)}"
[ -n "$P" ] || die "حدد المشروع: bash update-server.sh <PROJECT_ID>"
if [ -z "${CF_TOKEN:-}" ]; then echo "الصق مفتاح Cloudflare ثم Enter (لن يظهر):"; read -rs CF_TOKEN; echo; fi
CF() { curl -fsS --retry 6 --retry-delay 5 --retry-all-errors -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" "$@"; }
CF https://api.cloudflare.com/client/v4/user/tokens/verify >/dev/null || die "مفتاح Cloudflare غير صالح."
ACC=$(CF https://api.cloudflare.com/client/v4/accounts | python3 -c 'import json,sys; r=json.load(sys.stdin)["result"]; print(r[0]["id"] if r else "")')
SUB=$(CF "https://api.cloudflare.com/client/v4/accounts/$ACC/workers/subdomain" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("result") or {}).get("subdomain",""))')

say "1/2 تحديث خادم الإشعارات…"
export CLOUDFLARE_API_TOKEN="$CF_TOKEN" CLOUDFLARE_ACCOUNT_ID="$ACC"
W="npx -y wrangler@3"
( cd push-worker && $W deploy --var "PROJECT_ID:$P" ) || die "فشل نشر الخادم."
curl -fsS "https://medk-push.$SUB.workers.dev/" | grep -q medk-push && ok "الخادم يعمل" || say "قد يحتاج الخادم دقيقة."

say "2/2 نشر قواعد الحماية…"
bash ./deploy.sh "$P"

say "✅ تم تحديث الخادم والقواعد."
