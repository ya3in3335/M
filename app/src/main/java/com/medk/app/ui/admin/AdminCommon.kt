package com.medk.app.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medk.app.MedkApp
import com.medk.app.core.friendly
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "—", onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = MedkShapes.Card,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MedkColors.Violet,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem({ Text(l) }, onClick = { onSelect(v); open = false }) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = MedkColors.Purple))
    }
}

/** Picks an image, uploads it to the public product-images bucket and returns the URL. */
@Composable
fun ImageUploadBox(url: String?, onUploaded: (String) -> Unit, size: Dp = 96.dp, folder: String = "products", label: String = "رفع صورة") {
    val c = MedkApp.container
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var uploading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            try { onUploaded(c.admin.uploadImage(uri, folder)) } catch (e: Exception) { toast(e.friendly()) }
            uploading = false
        }
    }
    Box(Modifier.size(size).clip(MedkShapes.Card).border(1.dp, MaterialTheme.colorScheme.outline, MedkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(enabled = !uploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) NetImage(url, Modifier.fillMaxSize())
        if (uploading) CircularProgressIndicator(Modifier.size(28.dp), color = MedkColors.Violet)
        else if (url.isNullOrBlank()) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.AddPhotoAlternate, null, tint = MedkColors.Violet)
            Spacer(Modifier.width(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

fun String.toPriceOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
fun Double.plain(): String = if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()
