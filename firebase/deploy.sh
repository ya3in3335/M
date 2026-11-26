#!/usr/bin/env bash
# MED-K — نشر الخادم على Firebase (الخطة المجانية Spark) من Cloud Shell بأمر واحد.
# الاستعمال:  bash deploy.sh [PROJECT_ID]
# لا يحتاج خطة Blaze: ينشر قواعد الحماية والفهارس لقاعدة Firestore فقط.
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }

P="${1:-$(gcloud config get-value project 2>/dev/null || true)}"
OWNER="${OWNER_EMAILS:-$(gcloud config get-value account 2>/dev/null || true)}"
[ -n "$P" ] || die "لم يتم تحديد المشروع. نفّذ: gcloud config set project <PROJECT_ID>"
[ -n "$OWNER" ] || die "لم يتم العثور على بريد المالك. نفّذ: OWNER_EMAILS=you@mail.com bash deploy.sh"

say "المشروع: $P"
say "بريد المالك: $OWNER"

# owner e-mails → ['a@x.com','b@y.com'] injected into the security rules
LIST=$(echo "$OWNER" | tr 'A-Z' 'a-z' | tr -d ' ' | awk -F, '{for(i=1;i<=NF;i++) if($i!="") printf "%s\x27%s\x27", (n++?",":""), $i}')
sed "s/__OWNER_EMAILS__/[$LIST]/" firestore.rules.tmpl > firestore.rules
echo "{\"projects\":{\"default\":\"$P\"}}" > .firebaserc

say "1/3 قاعدة البيانات Firestore…"
gcloud services enable firestore.googleapis.com --project "$P" >/dev/null 2>&1 || true
if ! gcloud firestore databases describe --project "$P" >/dev/null 2>&1; then
  gcloud firestore databases create --location=europe-west1 --project "$P" \
    || die "تعذر إنشاء قاعدة البيانات. أنشئها من Firebase Console ← Firestore Database ← Create database ثم أعد التشغيل."
fi

FB="npx -y firebase-tools@13"
say "2/3 تسجيل الدخول إلى Firebase…"
if ! $FB projects:list >/dev/null 2>&1; then
  say "افتح الرابط، وافق، ثم الصق الرمز هنا"
  $FB login --no-localhost
fi

say "3/3 نشر قواعد الحماية والفهارس…"
$FB deploy --only firestore --project "$P" --force || die "فشل النشر — أرسل صورة الخطأ."

say "✅ تم! سجّل الدخول في MED-K Admin ببريدك ($OWNER) بعد تأكيده."
