#!/usr/bin/env bash
# MED-K — نشر الخادم على Firebase من Cloud Shell بأمر واحد.
# الاستعمال:  bash deploy.sh [PROJECT_ID]
# المشروع والبريد يُؤخذان تلقائياً من حساب Cloud Shell إن لم تحددهما.
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

echo "{\"projects\":{\"default\":\"$P\"}}" > .firebaserc
if [ ! -f "functions/.env.$P" ]; then
  # App Check is relaxed until the app is registered; set ENFORCE_APP_CHECK=true afterwards.
  printf 'OWNER_EMAILS=%s\nENFORCE_APP_CHECK=false\n' "$OWNER" > "functions/.env.$P"
fi

say "1/4 تفعيل الخدمات المطلوبة…"
gcloud services enable firestore.googleapis.com firebasestorage.googleapis.com cloudfunctions.googleapis.com \
  cloudbuild.googleapis.com artifactregistry.googleapis.com run.googleapis.com eventarc.googleapis.com \
  firebaserules.googleapis.com --project "$P" \
  || die "تعذر تفعيل الخدمات — تأكد أن المشروع على خطة Blaze (الدفع حسب الاستخدام)."

say "2/4 قاعدة البيانات Firestore…"
if ! gcloud firestore databases describe --project "$P" >/dev/null 2>&1; then
  gcloud firestore databases create --location=europe-west1 --project "$P"
fi

say "3/4 تجهيز دوال الخادم…"
npm ci --prefix functions --no-audit --no-fund

FB="npx -y firebase-tools@13"
if ! $FB projects:list >/dev/null 2>&1; then
  say "تسجيل الدخول: افتح الرابط، وافق، ثم الصق الرمز هنا"
  $FB login --no-localhost
fi

say "4/4 النشر (القواعد + الفهارس + الدوال)… قد يستغرق 3–5 دقائق"
$FB deploy --only firestore,storage,functions --project "$P" --force \
  || die "فشل النشر. إذا ظهر خطأ Storage: افتح Firebase Console ← Storage ← Get started ثم أعد تشغيل نفس الأمر."

say "✅ تم! سجّل الدخول في MED-K Admin ببريدك ($OWNER)."
