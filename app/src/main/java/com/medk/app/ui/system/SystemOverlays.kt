package com.medk.app.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medk.app.BuildConfig
import com.medk.app.MedkApp
import com.medk.app.core.AppUpdate
import com.medk.app.core.DownloadState
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.delay

/** Full-screen block shown while there is no working internet connection. */
@Composable
fun OfflineScreen(onRetry: () -> Unit) {
    var retrying by remember { mutableStateOf(false) }
    LaunchedEffect(retrying) { if (retrying) { delay(1200); retrying = false } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(MedkColors.Warning.copy(.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.WifiOff, null, tint = MedkColors.Warning, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("لا يوجد اتصال بالإنترنت", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("يعمل MED-K بالاتصال فقط لحماية طلباتك وبياناتك. سيعود التطبيق تلقائياً عند عودة الاتصال.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (retrying) "جارٍ التحقق…" else "إعادة المحاولة", { retrying = true; onRetry() },
                Modifier.width(220.dp), loading = retrying)
        }
    }
}

/** Update dialog (once per session) + a persistent banner with an "Update" button. */
@Composable
fun UpdatePrompt() {
    val u = MedkApp.container.updates
    val update by u.update.collectAsStateWithLifecycle()
    val download by u.download.collectAsStateWithLifecycle()
    // "Later" only hides the dialog for a short while: it comes back every 2 minutes and every time the app is reopened,
    // and the red banner stays on screen until the app is updated.
    var dialogDismissed by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { dialogDismissed = false; onPauseOrDispose { } }
    LaunchedEffect(dialogDismissed) { if (dialogDismissed) { kotlinx.coroutines.delay(120_000); dialogDismissed = false } }
    val up = update ?: return

    if (up.required || !dialogDismissed) UpdateDialog(up, download, onUpdate = { action(up, download) },
        onLater = if (up.required) null else ({ dialogDismissed = true }))
    else Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(12.dp)).background(MedkColors.Danger).clickable { action(up, download) }
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("نسختك قديمة — حدّث التطبيق الآن", color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text(when (val d = download) { is DownloadState.Downloading -> "جارٍ التنزيل… ${d.percent}%"
                is DownloadState.Ready -> "التحديث جاهز، اضغط للتثبيت"; else -> "الإصدار ${up.versionName} متوفر" },
                color = Color.White.copy(.85f), style = MaterialTheme.typography.labelSmall)
        }
        Text("تحديث", color = MedkColors.Danger, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

private fun action(up: AppUpdate, d: DownloadState) {
    val u = MedkApp.container.updates
    if (d is DownloadState.Ready) u.install(d.file) else u.startDownload()
}

@Composable
internal fun UpdateDialog(up: AppUpdate, d: DownloadState, onUpdate: () -> Unit, onLater: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = { onLater?.invoke() },
        icon = { Icon(Icons.Filled.SystemUpdate, null, tint = MedkColors.Violet) },
        title = { Text(if (up.required) "تحديث إلزامي" else "تحديث جديد متوفر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الإصدار ${up.versionName} متوفر (لديك ${BuildConfig.VERSION_NAME}).", style = MaterialTheme.typography.bodyMedium)
                if (!up.notes.isNullOrBlank()) Text(up.notes, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8)
                when (d) {
                    is DownloadState.Downloading -> {
                        LinearProgressIndicator(progress = { d.percent / 100f }, Modifier.fillMaxWidth(), color = MedkColors.Violet)
                        Text("جارٍ التنزيل… ${d.percent}%", style = MaterialTheme.typography.labelMedium)
                    }
                    is DownloadState.Ready -> Text("اكتمل التنزيل. اضغط «تثبيت». إذا طُلب منك، اسمح بتثبيت التطبيقات من MED-K ثم ارجع واضغط «تثبيت» مجدداً.",
                        style = MaterialTheme.typography.labelMedium, color = MedkColors.Success)
                    is DownloadState.Failed -> Text(d.message, style = MaterialTheme.typography.labelMedium, color = MedkColors.Danger)
                    DownloadState.Idle -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onUpdate, enabled = d !is DownloadState.Downloading) {
                Text(when (d) { is DownloadState.Ready -> "تثبيت"; is DownloadState.Failed -> "إعادة المحاولة"; else -> "تحديث الآن" },
                    color = MedkColors.Violet)
            }
        },
        dismissButton = { if (onLater != null) TextButton(onLater) { Text("لاحقاً") } },
    )
}
