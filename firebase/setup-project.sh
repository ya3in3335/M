#!/usr/bin/env bash
# MED-K — إنشاء مشروع Firebase جديد وتجهيزه بالكامل (مجاني، بدون بطاقة دفع) من Google Cloud Shell.
# الاستعمال:  bash setup-project.sh [PROJECT_ID]
# لمالك ببريد مختلف عن حساب Cloud Shell:  OWNER_EMAILS=you@mail.com bash setup-project.sh
set -euo pipefail
cd "$(dirname "$0")"

say()  { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✓ %s\033[0m\n' "$*"; }
die()  { printf '\n\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }
FB="npx -y firebase-tools@13"

ACCOUNT="$(gcloud config get-value account 2>/dev/null || true)"
[ -n "$ACCOUNT" ] || die "Cloud Shell غير مسجّل بحساب Google."
P="$(echo "${1:-medk-app-$(date +%y%m%d)-$((RANDOM % 9000 + 1000))}" | tr 'A-Z' 'a-z')"
SHA1="D2:19:CF:59:3E:7D:1D:CC:AF:1B:3F:7D:84:CB:1D:D3:A2:8B:7D:EB"
SHA256="27:C7:47:8C:15:04:DA:DD:20:EA:E5:92:49:D0:A1:6C:FB:9D:53:C6:DF:DD:53:9C:E2:8E:F4:9F:98:1B:51:2A"

OWNER="${OWNER_EMAILS:-$ACCOUNT}"
say "الحساب: $ACCOUNT"
say "بريد المالك: $OWNER"
say "المشروع: $P (الاسم: MED-K)"

say "1/8 إنشاء المشروع…"
if gcloud projects describe "$P" >/dev/null 2>&1; then ok "موجود مسبقاً"
else gcloud projects create "$P" --name="MED-K" || die "تعذر إنشاء المشروع (ربما بلغت الحد الأقصى لعدد المشاريع)."; ok "تم"; fi
gcloud config set project "$P" >/dev/null 2>&1

say "2/8 تفعيل الخدمات (Firestore + Authentication)…"
gcloud services enable firebase.googleapis.com firestore.googleapis.com identitytoolkit.googleapis.com \
  firebaserules.googleapis.com firebaseappcheck.googleapis.com --project "$P"
ok "تم"

say "3/8 تسجيل الدخول إلى أداة Firebase…"
if ! $FB projects:list >/dev/null 2>&1; then
  printf '  افتح الرابط، وافق، ثم الصق الرمز هنا\n'
  $FB login --no-localhost
fi
ok "تم"

say "4/8 إضافة Firebase إلى المشروع…"
$FB projects:addfirebase "$P" >/dev/null 2>&1 || true
$FB projects:list 2>/dev/null | grep -q "$P" || die "تعذر إضافة Firebase. افتح https://console.firebase.google.com وأضف المشروع $P يدوياً ثم أعد التشغيل."
ok "تم"

say "5/8 قاعدة البيانات Firestore…"
if gcloud firestore databases describe --project "$P" >/dev/null 2>&1; then ok "موجودة"
else gcloud firestore databases create --location=europe-west1 --project "$P" >/dev/null; ok "تم الإنشاء (europe-west1)"; fi

say "6/8 تفعيل تسجيل الدخول بالبريد وكلمة المرور…"
TOKEN="$(gcloud auth print-access-token)"
api() { curl -s -X "$1" -H "Authorization: Bearer $TOKEN" -H "X-Goog-User-Project: $P" -H "Content-Type: application/json" "$2" ${3:+-d "$3"}; }
CFG="https://identitytoolkit.googleapis.com/admin/v2/projects/$P/config"
api GET "$CFG" | grep -q '"name"' || api POST "https://identitytoolkit.googleapis.com/v2/projects/$P/identityPlatform:initializeAuth" "{}" >/dev/null
api PATCH "$CFG?updateMask=signIn.email.enabled,signIn.email.passwordRequired" \
  '{"signIn":{"email":{"enabled":true,"passwordRequired":true}}}' | grep -q '"enabled": true' && ok "تم" \
  || printf '  ⚠ لم يتم التأكيد — فعّله يدوياً من الرابط في الخطوة الأخيرة.\n'

say "7/8 تسجيل تطبيقي Android (MED-K و MED-K Admin)…"
app_id() { $FB apps:list ANDROID --project "$P" --json 2>/dev/null | python3 -c "
import json,sys
d=json.load(sys.stdin).get('result',[])
print(next((a['appId'] for a in d if a.get('packageName')=='$1'),''))"; }
for PKG in com.medk.app com.medk.admin; do
  ID="$(app_id $PKG)"
  if [ -z "$ID" ]; then
    NAME=$([ "$PKG" = "com.medk.admin" ] && echo "MED-K Admin" || echo "MED-K")
    $FB apps:create ANDROID "$NAME" --package-name "$PKG" --project "$P" >/dev/null
    ID="$(app_id $PKG)"
  fi
  [ -n "$ID" ] || die "تعذر تسجيل التطبيق $PKG"
  $FB apps:android:sha:create "$ID" "$SHA1" --project "$P" >/dev/null 2>&1 || true
  $FB apps:android:sha:create "$ID" "$SHA256" --project "$P" >/dev/null 2>&1 || true
  ok "$PKG"
done

say "8/8 نشر قواعد الحماية (بريد المالك: $OWNER)…"
OWNER_EMAILS="$OWNER" bash deploy.sh "$P" >/dev/null && ok "تم" || die "فشل نشر القواعد."

printf '\n\033[1;33m══════════════════════════════════════════════\n'
printf ' خطوة يدوية واحدة (Google لا يسمح بها برمجياً):\n'
printf ' افتح هذا الرابط ← Google ← Enable ← اختر بريدك ← Save\n'
printf ' https://console.firebase.google.com/project/%s/authentication/providers\n' "$P"
printf '══════════════════════════════════════════════\033[0m\n'
read -r -p "  بعد التفعيل اضغط Enter هنا… " _

say "تجهيز ملف الربط google-services.json…"
rm -rf .cfg && mkdir .cfg
for PKG in com.medk.app com.medk.admin; do $FB apps:sdkconfig ANDROID "$(app_id $PKG)" --project "$P" --out ".cfg/$PKG.json" >/dev/null; done
python3 - <<'PY'
import json
a=json.load(open('.cfg/com.medk.app.json')); b=json.load(open('.cfg/com.medk.admin.json'))
a['client']=a['client']+[c for c in b['client'] if c not in a['client']]
json.dump(a,open('google-services.json','w'),indent=2)
web=[o for c in a['client'] for o in c.get('oauth_client',[]) if o.get('client_type')==3]
print('  ✓ تسجيل Google جاهز' if web else '  ⚠ عميل Google غير موجود بعد — تأكد من تفعيل Google ثم أعد تشغيل الأمر')
PY
rm -rf .cfg
cloudshell download google-services.json 2>/dev/null || true
say "✅ تم! سيبدأ تنزيل الملف google-services.json على هاتفك — أرسله في المحادثة."
printf '  معرّف المشروع: %s\n' "$P"
