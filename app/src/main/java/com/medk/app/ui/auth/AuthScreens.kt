package com.medk.app.ui.auth

import android.util.Patterns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medk.app.MedkApp
import com.medk.app.core.friendly
import com.medk.app.data.GoogleSignInCancelled
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.MedkMark
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.MedkWordmark
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.categoryIcon
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.categoryPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

// ===================================================================== VM
class AuthViewModel : ViewModel() {
    private val repo = MedkApp.container.auth
    private val _busy = MutableStateFlow<String?>(null) // "email" | "google" | null
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _resetSent = MutableStateFlow(false)
    val resetSent = _resetSent.asStateFlow()

    fun clearError() { _error.value = null }

    private fun run(kind: String, block: suspend () -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = kind; _error.value = null
            try { block() } catch (_: GoogleSignInCancelled) {
            } catch (e: Exception) { _error.value = e.friendly() } finally { _busy.value = null }
        }
    }

    fun login(email: String, password: String) = run("email") { repo.signIn(email, password) }
    fun register(name: String, email: String, password: String) = run("email") { repo.register(name, email, password) }
    fun google(ctx: android.content.Context) = run("google") { repo.signInWithGoogle(ctx) }
    fun reset(email: String) = run("email") { repo.sendPasswordReset(email); _resetSent.value = true }
    fun demo(role: com.medk.app.data.Role) = (repo as? com.medk.app.data.demo.DemoAuthRepository)?.loginAs(role)
}

private fun validEmail(s: String) = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

// ================================================================= splash
@Composable
fun SplashScreen() {
    val scale = remember { Animatable(.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        alpha.animateTo(1f, tween(600))
    }
    val t = rememberInfiniteTransition(label = "dots")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF160A2B), MedkColors.Black, MedkColors.Black))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale.value).alpha(alpha.value)) {
            MedkMark(88.dp)
            Spacer(Modifier.height(16.dp))
            MedkWordmark(30.sp, subtitle = true)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                val a by t.animateFloat(.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse), label = "d$i")
                Box(Modifier.size(9.dp).alpha(a).clip(CircleShape).background(MedkColors.Violet))
            }
        }
    }
}

// ================================================================ welcome
@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit, vm: AuthViewModel = viewModel()) {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()
        .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        MedkWordmark(22.sp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MedkMark(96.dp, Modifier.offset(y = y.dp))
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("gaming", "subscriptions", "accounts", "p2p", "other").forEach { key ->
                        Box(Modifier.size(42.dp).clip(CircleShape).background(categoryPalette(key).brush), contentAlignment = Alignment.Center) {
                            Icon(categoryIcon(key), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Text("كل خدماتك الرقمية في مكان واحد", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("شحن الألعاب، اشتراكات التطبيقات، الحسابات، العملات الرقمية P2P وبطاقات الهدايا — بأمان وسرعة مع فريق مبيعات حقيقي.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        DemoLoginPanel(vm)
        Spacer(Modifier.height(if (com.medk.app.BuildConfig.DEMO) 14.dp else 8.dp))
        PrimaryButton("تسجيل الدخول", onLogin)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("إنشاء حساب", onRegister)
        Spacer(Modifier.height(8.dp))
    }
}

// ============================================================ demo panel
/** One-tap sign-in for the demo flavor (no backend). */
@Composable
fun DemoLoginPanel(vm: AuthViewModel) {
    if (!com.medk.app.BuildConfig.DEMO) return
    Column(Modifier.fillMaxWidth().clip(com.medk.app.ui.theme.MedkShapes.Chamfer).background(MedkColors.Purple.copy(.10f))
        .border(1.dp, MedkColors.Purple.copy(.5f), com.medk.app.ui.theme.MedkShapes.Chamfer).padding(12.dp)) {
        Text("نسخة تجريبية — بدون خادم", style = MaterialTheme.typography.titleSmall, color = MedkColors.Violet)
        Text("ادخل مباشرة بأحد الأدوار لتجربة التطبيق ببيانات تجريبية:", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (com.medk.app.BuildConfig.ADMIN_APP) listOf(com.medk.app.data.Role.OWNER to "دخول كمالك")
            else listOf(com.medk.app.data.Role.CUSTOMER to "دخول كعميل", com.medk.app.data.Role.STAFF to "دخول كبائع"))
                .forEach { (r, l) -> PrimaryButton(l, { vm.demo(r) }, Modifier.weight(1f), height = 36.dp) }
        }
    }
}

// ================================================================== shared
@Composable
private fun AuthScaffold(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        if (com.medk.app.BuildConfig.ADMIN_APP) Spacer(Modifier.height(24.dp)) else MedkTopBar("", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            MedkMark(48.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ErrorBanner(msg: String?) {
    if (msg == null) return
    Surface(color = MedkColors.Danger.copy(.12f), shape = MedkShapes.Small, border = BorderStroke(1.dp, MedkColors.Danger.copy(.4f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(msg, color = MedkColors.Danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun GoogleButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled && !loading, shape = com.medk.app.ui.theme.MedkShapes.ChamferSm,
        color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MedkColors.Violet)
            else {
                Icon(androidx.compose.ui.res.painterResource(com.medk.app.R.drawable.ic_google), contentDescription = "Google",
                    tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("المتابعة باستخدام Google", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  أو  ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.weight(1f))
    }
}

// =================================================================== login
@Composable
fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit, vm: AuthViewModel = viewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val emailErr = if (submitted && !validEmail(email)) "أدخل بريداً إلكترونياً صحيحاً" else null
    val passErr = if (submitted && password.isEmpty()) "أدخل كلمة المرور" else null

    val admin = com.medk.app.BuildConfig.ADMIN_APP
    AuthScaffold(if (admin) "لوحة تحكم MED-K" else "تسجيل الدخول", if (admin) "تسجيل دخول المالك" else "مرحباً بعودتك إلى MED-K", onBack) {
        DemoLoginPanel(vm)
        if (com.medk.app.BuildConfig.DEMO) Spacer(Modifier.height(16.dp))
        ErrorBanner(error)
        MedkTextField(email, { email = it; vm.clearError() }, "البريد الإلكتروني", leading = Icons.Filled.Email,
            keyboardType = KeyboardType.Email, error = emailErr)
        Spacer(Modifier.height(12.dp))
        MedkTextField(password, { password = it; vm.clearError() }, "كلمة المرور", leading = Icons.Filled.Lock, isPassword = true, error = passErr)
        TextButton(onClick = onForgot) {
            Text("نسيت كلمة المرور؟", color = MedkColors.Violet)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("تسجيل الدخول", {
            submitted = true
            if (validEmail(email) && password.isNotEmpty()) vm.login(email, password)
        }, loading = busy == "email", enabled = busy == null)
        OrDivider()
        GoogleButton(busy == "google", busy == null) { vm.google(ctx) }
        Spacer(Modifier.height(16.dp))
        if (!admin) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("ليس لديك حساب؟", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRegister) { Text("إنشاء حساب", color = MedkColors.Violet, fontWeight = FontWeight.Bold) }
        }
    }
}

// ================================================================ register
@Composable
fun RegisterScreen(onBack: () -> Unit, onLogin: () -> Unit, vm: AuthViewModel = viewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val nameErr = if (submitted && name.trim().length < 2) "أدخل اسمك الكامل" else null
    val emailErr = if (submitted && !validEmail(email)) "أدخل بريداً إلكترونياً صحيحاً" else null
    val passErr = if (submitted && password.length < 8) "8 أحرف على الأقل" else null
    val confErr = if (submitted && confirm != password) "كلمتا المرور غير متطابقتين" else null

    AuthScaffold("إنشاء حساب", "انضم إلى MED-K وابدأ التسوق", onBack) {
        ErrorBanner(error)
        MedkTextField(name, { name = it }, "الاسم الكامل", leading = Icons.Filled.Person, error = nameErr)
        Spacer(Modifier.height(12.dp))
        MedkTextField(email, { email = it; vm.clearError() }, "البريد الإلكتروني", leading = Icons.Filled.Email,
            keyboardType = KeyboardType.Email, error = emailErr)
        Spacer(Modifier.height(12.dp))
        MedkTextField(password, { password = it }, "كلمة المرور", leading = Icons.Filled.Lock, isPassword = true, error = passErr)
        Spacer(Modifier.height(12.dp))
        MedkTextField(confirm, { confirm = it }, "تأكيد كلمة المرور", leading = Icons.Filled.Lock, isPassword = true, error = confErr)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("إنشاء حساب", {
            submitted = true
            if (name.trim().length >= 2 && validEmail(email) && password.length >= 8 && confirm == password)
                vm.register(name, email, password)
        }, loading = busy == "email", enabled = busy == null)
        OrDivider()
        GoogleButton(busy == "google", busy == null) { vm.google(ctx) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("لديك حساب بالفعل؟", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onLogin) { Text("تسجيل الدخول", color = MedkColors.Violet, fontWeight = FontWeight.Bold) }
        }
    }
}

// ================================================================ recovery
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, vm: AuthViewModel = viewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val sent by vm.resetSent.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    AuthScaffold("استعادة كلمة المرور", "أدخل بريدك وسنرسل لك رابط إعادة التعيين", onBack) {
        if (sent) {
            Box(Modifier.fillMaxWidth().clip(MedkShapes.Card).background(MedkColors.Success.copy(.1f))
                .border(1.dp, MedkColors.Success.copy(.4f), MedkShapes.Card)) {
                EmptyBox(Icons.Filled.CheckCircle, "تم إرسال الرابط",
                    "تحقق من صندوق بريدك ($email) واتبع الرابط لتعيين كلمة مرور جديدة. تحقق أيضاً من مجلد الرسائل غير المرغوب فيها.",
                    "العودة لتسجيل الدخول", onBack)
            }
        } else {
            ErrorBanner(error)
            MedkTextField(email, { email = it; vm.clearError() }, "البريد الإلكتروني", leading = Icons.Filled.Email,
                keyboardType = KeyboardType.Email, error = if (submitted && !validEmail(email)) "أدخل بريداً إلكترونياً صحيحاً" else null)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("إرسال رابط الاستعادة", {
                submitted = true; if (validEmail(email)) vm.reset(email)
            }, loading = busy != null)
        }
    }
}

/** Shown when Firebase / backend configuration is missing from the build. */
@Composable
fun NotConfiguredScreen() {
    val c = MedkApp.container
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyBox(Icons.Filled.Lock, "التطبيق غير مُهيأ",
            buildString {
                append("هذه النسخة بُنيت بدون إعدادات الخادم.\n")
                append("• ملف google-services.json (Firebase) مفقود أو لا يحتوي على معرّف هذا التطبيق.\n")
                append("راجع ملف README لإعداد GitHub Secrets.")
            })
    }
}

@Composable
fun AuthFailedScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center) {
        com.medk.app.ui.components.ErrorBox(message, onRetry)
        SecondaryButton("تسجيل الخروج", { scope.launch { onSignOut() } })
    }
}


/** After e-mail sign-up: the app stays locked until the link in the confirmation e-mail is opened. */
@Composable
fun VerifyEmailScreen(email: String) {
    val c = MedkApp.container
    val scope = rememberCoroutineScope()
    val toast = com.medk.app.ui.components.LocalToast.current
    var busy by remember { mutableStateOf(false) }
    var cooldown by remember { mutableStateOf(60) }
    LaunchedEffect(Unit) {   // the account unlocks by itself a few seconds after the link is opened
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (cooldown > 0) cooldown--
            if (cooldown % 5 == 0) runCatching { c.auth.checkEmailVerified() }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MedkColors.Purple.copy(.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Email, null, tint = MedkColors.Violet, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("أكّد بريدك الإلكتروني", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("أرسلنا رابط التأكيد إلى", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(email, style = MaterialTheme.typography.titleMedium, color = MedkColors.Violet)
        Spacer(Modifier.height(8.dp))
        Text("افتح بريدك واضغط على الرابط، ثم ارجع هنا. إن لم تجده فابحث في «الرسائل غير المرغوب فيها» (Spam).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("أكّدت بريدي — متابعة", {
            busy = true
            scope.launch {
                val ok = runCatching { c.auth.checkEmailVerified() }.getOrElse { toast(it.friendly()); null }
                if (ok == false) toast("لم يتم التأكيد بعد. افتح الرابط في البريد أولاً.")
                busy = false
            }
        }, loading = busy)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(if (cooldown > 0) "إعادة إرسال الرابط (${cooldown})" else "إعادة إرسال الرابط", {
            scope.launch {
                runCatching { c.auth.resendVerification() }
                    .onSuccess { toast("تم إرسال رابط جديد إلى $email"); cooldown = 60 }
                    .onFailure { toast(it.friendly()) }
            }
        }, enabled = cooldown == 0)
        Spacer(Modifier.height(10.dp))
        TextButton({ scope.launch { c.auth.signOut(c.context) } }) { Text("تسجيل الخروج / استعمال بريد آخر") }
    }
}
