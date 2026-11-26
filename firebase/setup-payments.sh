#!/usr/bin/env bash
# MED-K — تفعيل الدفع داخل التطبيق بعملة USDT (بوابة NOWPayments) على خادم MED-K في Cloudflare.
# يُشغَّل بعد setup-push.sh. الاستعمال في Cloud Shell:
#   OWNER_EMAILS=you@mail.com bash setup-payments.sh [PROJECT_ID]
#   (للتجربة بأموال وهمية: SANDBOX=1 ومفتاح من حساب sandbox.nowpayments.io)
# يطلب مفتاح Cloudflare ومفتاح NOWPayments (لا يظهران أثناء اللصق ولا يُحفظان على القرص).
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }

P="${1:-$(gcloud config get-value project 2>/dev/null || true)}"
[ -n "$P" ] || die "حدد المشروع: bash setup-payments.sh <PROJECT_ID>"
SANDBOX="${SANDBOX:-0}"
say "المشروع: $P $( [ "$SANDBOX" = 1 ] && echo '(وضع تجريبي)' )"

say "1/4 مفتاح Cloudflare"
echo "الصق مفتاح Cloudflare ثم Enter (لن يظهر):"
read -rs CF_TOKEN; echo
CF() { curl -fsS -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" "$@"; }
CF https://api.cloudflare.com/client/v4/user/tokens/verify >/dev/null || die "مفتاح Cloudflare غير صالح."
ACC=$(CF https://api.cloudflare.com/client/v4/accounts | python3 -c 'import json,sys; r=json.load(sys.stdin)["result"]; print(r[0]["id"] if r else "")')
[ -n "$ACC" ] || die "المفتاح لا يملك صلاحية الحساب. استعمل القالب: Edit Cloudflare Workers"
SUB=$(CF "https://api.cloudflare.com/client/v4/accounts/$ACC/workers/subdomain" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("result") or {}).get("subdomain",""))')
[ -n "$SUB" ] || die "شغّل setup-push.sh أولاً."
ok "تم"

say "2/4 مفتاح NOWPayments"
echo "الصق مفتاح API من NOWPayments ثم Enter (لن يظهر):"
read -rs NP_KEY; echo
NPB=$([ "$SANDBOX" = 1 ] && echo https://api-sandbox.nowpayments.io/v1 || echo https://api.nowpayments.io/v1)
curl -fsS -H "x-api-key: $NP_KEY" "$NPB/merchant/coins" | grep -qi usdttrc20 \
  || die "مفتاح NOWPayments غير صالح، أو عملة USDT (TRC20) غير مفعّلة في حسابك (Settings ← Payment settings)."
ok "المفتاح صالح وعملة USDT TRC20 مفعّلة"

say "3/4 تحديث الخادم…"
export CLOUDFLARE_API_TOKEN="$CF_TOKEN" CLOUDFLARE_ACCOUNT_ID="$ACC"
W="npx -y wrangler@3"
( cd push-worker && $W deploy --var "PROJECT_ID:$P" --var "NOWPAY_SANDBOX:$SANDBOX" ) || die "فشل نشر الخادم."
( cd push-worker && printf '%s' "$NP_KEY" | $W secret put NOWPAY_API_KEY ) || die "فشل حفظ مفتاح NOWPayments."
URL="https://medk-push.$SUB.workers.dev"
for i in 1 2 3 4 5 6; do curl -fsS "$URL/" 2>/dev/null | grep -q '"payments":true' && break; sleep 5; done
curl -fsS "$URL/" | grep -q '"payments":true' && ok "الخادم جاهز للدفع: $URL" || say "تم النشر — قد يحتاج الخادم دقيقة."

# الإعداد الافتراضي: الدفع مفعّل وعمولة 10% (لا يغيّر إعداداً موجوداً)
curl -fsS -o /dev/null -X PATCH -H "Authorization: Bearer $(gcloud auth print-access-token)" -H "Content-Type: application/json" \
  "https://firestore.googleapis.com/v1/projects/$P/databases/(default)/documents/config/payments?currentDocument.exists=false" \
  -d '{"fields":{"enabled":{"booleanValue":true},"commissionPct":{"doubleValue":10}}}' 2>/dev/null || true

say "4/4 نشر قواعد الحماية…"
bash ./deploy.sh "$P"

say "✅ الدفع بـ USDT مفعّل! غيّر نسبة عمولتك من MED-K Admin ← المزيد ← المدفوعات."
