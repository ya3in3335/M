#!/usr/bin/env bash
# MED-K — تفعيل الإشعارات الفورية مجاناً (خادم Cloudflare Workers + Firebase Cloud Messaging).
# الاستعمال في Cloud Shell:
#   OWNER_EMAILS=you@mail.com bash setup-push.sh [PROJECT_ID]
# يطلب منك مفتاح Cloudflare (لا يظهر أثناء اللصق ولا يُحفظ على القرص).
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }

P="${1:-$(gcloud config get-value project 2>/dev/null || true)}"
[ -n "$P" ] || die "حدد المشروع: bash setup-push.sh <PROJECT_ID>"
gcloud config set project "$P" >/dev/null 2>&1
SA="medk-push@$P.iam.gserviceaccount.com"
TMP="$(mktemp -d)"; trap 'shred -u "$TMP"/* 2>/dev/null || rm -rf "$TMP"; rm -rf "$TMP"' EXIT

say "المشروع: $P"

# ── 1. مفتاح Cloudflare ─────────────────────────────────────────────────────
say "1/6 مفتاح Cloudflare"
echo "الصق المفتاح ثم اضغط Enter (لن يظهر على الشاشة):"
read -rs CF_TOKEN; echo
[ -n "$CF_TOKEN" ] || die "لم يتم لصق المفتاح."
CF() { curl -fsS --retry 6 --retry-delay 5 --retry-all-errors -H "Authorization: Bearer $CF_TOKEN" -H "Content-Type: application/json" "$@"; }
CF https://api.cloudflare.com/client/v4/user/tokens/verify >/dev/null \
  || die "المفتاح غير صالح. أنشئه من: dash.cloudflare.com/profile/api-tokens ← Create Token ← Edit Cloudflare Workers"
ACC=$(CF https://api.cloudflare.com/client/v4/accounts | python3 -c 'import json,sys; r=json.load(sys.stdin)["result"]; print(r[0]["id"] if r else "")')
[ -n "$ACC" ] || die "المفتاح لا يملك صلاحية الحساب. استعمل القالب: Edit Cloudflare Workers"
SUB=$(CF "https://api.cloudflare.com/client/v4/accounts/$ACC/workers/subdomain" 2>/dev/null \
      | python3 -c 'import json,sys; print((json.load(sys.stdin).get("result") or {}).get("subdomain",""))' 2>/dev/null || true)
if [ -z "$SUB" ]; then
  SUB="medk-$(head -c4 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  CF -X PUT "https://api.cloudflare.com/client/v4/accounts/$ACC/workers/subdomain" -d "{\"subdomain\":\"$SUB\"}" >/dev/null \
    || die "تعذر إنشاء النطاق الفرعي workers.dev — افتح dash.cloudflare.com ← Workers & Pages مرة واحدة ثم أعد التشغيل."
fi
ok "حساب Cloudflare جاهز ($SUB.workers.dev)"

# ── 2. خدمات Google ─────────────────────────────────────────────────────────
say "2/6 تفعيل خدمة الإشعارات (FCM)…"
gcloud services enable fcm.googleapis.com iam.googleapis.com firestore.googleapis.com --project "$P" >/dev/null
ok "تم"

# ── 3. حساب خدمة بصلاحيات محدودة (قراءة Firestore + إرسال الإشعارات فقط) ─────
say "3/6 حساب الخدمة medk-push…"
gcloud iam service-accounts describe "$SA" --project "$P" >/dev/null 2>&1 \
  || gcloud iam service-accounts create medk-push --display-name "MED-K push server" --project "$P" >/dev/null
# حساب الخدمة الجديد يحتاج بضع ثوانٍ ليصبح معروفاً في كل أنظمة جوجل → ننتظر ونعيد المحاولة.
retry() { local i; for i in $(seq 1 12); do "$@" >/dev/null 2>&1 && return 0; printf '.'; sleep 10; done; "$@"; }
for role in roles/datastore.user roles/firebasecloudmessaging.admin; do
  retry gcloud projects add-iam-policy-binding "$P" --member "serviceAccount:$SA" --role "$role" --condition=None \
    || die "تعذر إعطاء الصلاحية $role. أعد تشغيل نفس الأمر بعد دقيقة."
done
echo
# مفتاح واحد فقط نشط: نحذف المفاتيح القديمة لهذا الحساب
for k in $(gcloud iam service-accounts keys list --iam-account "$SA" --managed-by=user --format='value(name)' 2>/dev/null); do
  gcloud iam service-accounts keys delete "$k" --iam-account "$SA" --quiet >/dev/null 2>&1 || true
done
retry gcloud iam service-accounts keys create "$TMP/key.json" --iam-account "$SA" \
  || die "تعذر إنشاء مفتاح حساب الخدمة. أرسل صورة الخطأ."
ok "تم"

# ── 4. نشر الخادم على Cloudflare ─────────────────────────────────────────────
say "4/6 نشر خادم الإشعارات…"
export CLOUDFLARE_API_TOKEN="$CF_TOKEN" CLOUDFLARE_ACCOUNT_ID="$ACC"
W="npx -y wrangler@3"
( cd push-worker && $W deploy --var "PROJECT_ID:$P" ) || die "فشل نشر الخادم. أرسل صورة الخطأ."
( cd push-worker && $W secret put SA_KEY < "$TMP/key.json" ) || die "فشل حفظ مفتاح الخادم. أرسل صورة الخطأ."
URL="https://medk-push.$SUB.workers.dev"
for i in 1 2 3 4 5 6; do curl -fsS "$URL/" >/dev/null 2>&1 && break; sleep 5; done
curl -fsS "$URL/" | grep -q medk-push && ok "الخادم يعمل: $URL" || say "الخادم منشور ($URL) — قد يحتاج دقيقة ليصبح متاحاً."

# ── 5. حفظ عنوان الخادم في قاعدة البيانات (التطبيق يقرؤه تلقائياً) ─────────────
say "5/6 ربط التطبيق بالخادم…"
curl -fsS -X PATCH \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" -H "Content-Type: application/json" \
  "https://firestore.googleapis.com/v1/projects/$P/databases/(default)/documents/config/push" \
  -d "{\"fields\":{\"url\":{\"stringValue\":\"$URL\"}}}" >/dev/null || die "تعذر حفظ عنوان الخادم."
ok "تم"

# ── 6. نشر قواعد الحماية والفهارس الجديدة ────────────────────────────────────
say "6/6 نشر قواعد الحماية…"
bash ./deploy.sh "$P"

say "✅ الإشعارات الفورية مفعّلة! سجّل الخروج ثم الدخول في التطبيقين لتسجيل هاتفك."
