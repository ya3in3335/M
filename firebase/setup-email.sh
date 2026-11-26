#!/usr/bin/env bash
# MED-K — تفعيل رمز التحقق بالبريد (6 أرقام) عند التسجيل، عبر Brevo (مجاني 300 رسالة/يوم).
# الاستعمال في Cloud Shell:
#   CF_TOKEN=... BREVO_KEY=... MAIL_FROM=you@gmail.com OWNER_EMAILS=you@mail.com bash setup-email.sh [PROJECT_ID]
# MAIL_FROM: البريد المرسِل الذي أكّدته في Brevo (Senders).
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }

P="${1:-$(gcloud config get-value project 2>/dev/null || true)}"
[ -n "$P" ] || die "حدد المشروع: bash setup-email.sh <PROJECT_ID>"
gcloud config set project "$P" >/dev/null 2>&1
[ -n "${CF_TOKEN:-}" ] || { echo "الصق مفتاح Cloudflare ثم Enter (لن يظهر):"; read -rs CF_TOKEN; echo; }
[ -n "${BREVO_KEY:-}" ] || { echo "الصق مفتاح Brevo API ثم Enter (لن يظهر):"; read -rs BREVO_KEY; echo; }
[ -n "${MAIL_FROM:-}" ] || { echo "اكتب البريد المرسِل المؤكَّد في Brevo:"; read -r MAIL_FROM; }

say "1/4 فحص مفتاح Brevo…"
R=$(curl -sS --retry 4 --retry-all-errors -H "api-key: $BREVO_KEY" -H "accept: application/json" https://api.brevo.com/v3/senders || true)
echo "$R" | grep -q '"senders"' || die "مفتاح Brevo غير صالح: ${R:0:200}"
echo "$R" | grep -qi "\"$MAIL_FROM\"" || say "تنبيه: البريد $MAIL_FROM غير ظاهر في قائمة المرسلين في Brevo — أضفه وأكّده من Senders، وإلا لن تصل الرسائل."
ok "تم"

say "2/4 صلاحية تأكيد الحسابات لخادم MED-K…"
SA="medk-push@$P.iam.gserviceaccount.com"
for i in 1 2 3 4 5 6; do
  gcloud projects add-iam-policy-binding "$P" --member "serviceAccount:$SA" --role roles/firebaseauth.admin --condition=None >/dev/null 2>&1 && break
  printf '.'; sleep 10
done
gcloud services enable identitytoolkit.googleapis.com --project "$P" >/dev/null 2>&1 || true
ok "تم"

say "3/4 تحديث الخادم وحفظ المفاتيح…"
CF() { curl -fsS --retry 6 --retry-delay 5 --retry-all-errors -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" "$@"; }
ACC=$(CF https://api.cloudflare.com/client/v4/accounts | python3 -c 'import json,sys; r=json.load(sys.stdin)["result"]; print(r[0]["id"] if r else "")')
SUB=$(CF "https://api.cloudflare.com/client/v4/accounts/$ACC/workers/subdomain" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("result") or {}).get("subdomain",""))')
export CLOUDFLARE_API_TOKEN="$CF_TOKEN" CLOUDFLARE_ACCOUNT_ID="$ACC"
W="npx -y wrangler@3"
( cd push-worker && $W deploy --var "PROJECT_ID:$P" ) || die "فشل نشر الخادم."
( cd push-worker && printf '%s' "$BREVO_KEY" | $W secret put BREVO_API_KEY ) || die "فشل حفظ مفتاح Brevo."
( cd push-worker && printf '%s' "$MAIL_FROM" | $W secret put MAIL_FROM ) || die "فشل حفظ البريد المرسِل."
curl -fsS "https://medk-push.$SUB.workers.dev/" | grep -q medk-push && ok "الخادم يعمل" || say "قد يحتاج الخادم دقيقة."

say "4/4 نشر قواعد الحماية…"
bash ./deploy.sh "$P"

say "✅ رمز التحقق بالبريد مفعّل! جرّب إنشاء حساب جديد في MED-K."
