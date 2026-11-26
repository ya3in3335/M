package com.medk.app.core

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Generic UI state used by every screen. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** Turns any thrown error into a readable Arabic message. */
fun Throwable.friendly(): String {
    val raw = when (this) {
        is FirebaseFirestoreException -> "${code.name} ${message.orEmpty()}"
        else -> message.orEmpty()
    }
    val m = raw.lowercase()
    return when {
        this is FirebaseNetworkException || (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE) ||
            this is IOException || m.contains("unable to resolve host") || m.contains("timeout") || m.contains("failed to connect") ->
            "تعذر الاتصال بالخادم. تحقق من اتصالك بالإنترنت."
        m.contains("password is invalid") || m.contains("invalid_login") || m.contains("credential is incorrect") ||
            m.contains("invalid-credential") || m.contains("supplied auth credential") -> "البريد الإلكتروني أو كلمة المرور غير صحيحة."
        m.contains("no user record") || m.contains("user-not-found") -> "لا يوجد حساب بهذا البريد الإلكتروني."
        m.contains("already in use") -> "هذا البريد الإلكتروني مستخدم بالفعل."
        m.contains("badly formatted") -> "صيغة البريد الإلكتروني غير صحيحة."
        m.contains("weak") && m.contains("password") -> "كلمة المرور ضعيفة (6 أحرف على الأقل)."
        m.contains("too many") || m.contains("blocked all requests") -> "محاولات كثيرة، حاول لاحقاً."
        m.contains("account_disabled") || m.contains("user account has been disabled") -> "هذا الحساب موقوف. تواصل مع الإدارة."
        m.contains("recent") && m.contains("login") -> "لأسباب أمنية، سجّل الدخول من جديد ثم أعد المحاولة."
        m.contains("email_not_verified") -> "أكّد بريدك الإلكتروني أولاً لإتمام الطلب. أرسلنا لك رابط التأكيد — افتحه ثم أعد المحاولة."
        m.contains("insufficient stock") -> "الكمية المطلوبة غير متوفرة حالياً."
        m.contains("no available products") -> "المنتجات في السلة لم تعد متوفرة."
        m.contains("resource_exhausted") || m.contains("too many requests") -> "طلبات كثيرة خلال وقت قصير، انتظر دقيقة ثم أعد المحاولة."
        m.contains("app check") || m.contains("app_check") -> "تعذر التحقق من سلامة التطبيق. ثبّت النسخة الرسمية من التطبيق."
        m.contains("permission_denied") || m.contains("insufficient permissions") || m.contains("permission denied") ||
            m.contains("forbidden") || m.contains("owner only") ->
            "ليست لديك صلاحية لتنفيذ هذا الإجراء."
        m.contains("unauthenticated") || m.contains("401") -> "انتهت الجلسة، يرجى تسجيل الدخول مجدداً."
        m.contains("not signed in") -> "انتهت الجلسة، يرجى تسجيل الدخول مجدداً."
        m.contains("bad_email") -> "البريد الإلكتروني غير صحيح."
        m.contains("commission_range") -> "النسبة يجب أن تكون بين 0 و 50%."
        m.contains("requires an index") || m.contains("index is currently building") ->
            "قاعدة البيانات قيد التجهيز (الفهارس). أعد المحاولة بعد دقائق."
        m.contains("cannot change an owner") -> "لا يمكن تعديل حساب المالك."
        m.contains("not a seller") || m.contains("target is not a seller") -> "هذا الحساب ليس بائعاً."
        else -> message?.take(160) ?: "حدث خطأ غير متوقع."
    }
}

private val arLocale = Locale.forLanguageTag("ar-DZ")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

private fun parse(iso: String?) = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()

fun formatDateTime(iso: String?) = parse(iso)?.format(dateTimeFmt) ?: ""
fun formatTime(iso: String?) = parse(iso)?.format(timeFmt) ?: ""
fun formatDate(iso: String?) = parse(iso)?.format(dateFmt) ?: ""

/** Short relative-ish label for conversation lists. */
fun formatShort(iso: String?): String {
    val t = parse(iso) ?: return ""
    val now = java.time.ZonedDateTime.now()
    return if (t.toLocalDate() == now.toLocalDate()) t.format(timeFmt) else t.format(dateFmt)
}

fun usd(v: Double): String = "$" + NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(v)

fun dzd(v: Double): String = NumberFormat.getNumberInstance(Locale.US).apply {
    maximumFractionDigits = 0 }.format(v) + " د.ج"

@Suppress("unused") private val keepLocale = arLocale
