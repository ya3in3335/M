package com.medk.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.dzd
import com.medk.app.core.friendly
import com.medk.app.data.Banner
import com.medk.app.data.BannerInput
import com.medk.app.data.Category
import com.medk.app.data.P2pAsset
import com.medk.app.data.P2pAssetInput
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.data.Section
import com.medk.app.data.SectionInput
import com.medk.app.data.StaffPermissions
import com.medk.app.ui.components.Avatar
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.Pill
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.SecondaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.categoryIcon
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.theme.MedkColors
import com.medk.app.ui.theme.categoryPalette
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

// --------------------------------------------------------------- helpers
@Composable
private fun ManageScaffold(title: String, nav: NavHostController, fabLabel: String?, onFab: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Box(Modifier.statusBarsPadding()) { MedkTopBar(title, onBack = { nav.popBackStack() }) } },
        floatingActionButton = {
            if (fabLabel != null) ExtendedFloatingActionButton(onClick = onFab, shape = MedkShapes.ChamferSm, containerColor = MedkColors.Purple, contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text(fabLabel) })
        },
    ) { pad -> Box(Modifier.padding(pad)) { content() } }
}

@Composable
private fun FormDialog(title: String, busy: Boolean, valid: Boolean, onDismiss: () -> Unit, onSave: () -> Unit,
                       onDelete: (() -> Unit)? = null, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = { TextButton(onSave, enabled = valid && !busy) { Text(if (busy) "جارٍ الحفظ…" else "حفظ", color = MedkColors.Violet) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onDelete, enabled = !busy) { Text("حذف", color = MedkColors.Danger) }
                TextButton(onDismiss, enabled = !busy) { Text("إلغاء") }
            }
        },
    )
}

// ============================================================= categories
@Composable
fun AdminCategoriesScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-cats") { c.catalog.categories(force = true) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    val action = actionVm("cat-save")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    ManageScaffold("الأقسام الرئيسية", nav, null, {}) {
        StateView(state, { vm.load() }) { cats ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("الأقسام الخمسة الرئيسية مرتبطة بشاشات مخصصة. يمكنك تعديل الاسم والوصف والترتيب والإظهار.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(cats, key = { it.id }) { cat ->
                    val p = categoryPalette(cat.colorKey ?: cat.slug)
                    MedkCard(onClick = { editing = cat }, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(MedkShapes.Small).background(p.brush), contentAlignment = Alignment.Center) {
                                Icon(categoryIcon(cat.iconKey ?: cat.slug), null, tint = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleSmall)
                                Text(cat.description ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(if (cat.isActive) "ظاهر" else "مخفي", if (cat.isActive) MedkColors.Success else MedkColors.Danger)
                        }
                    }
                }
            }
        }
    }
    editing?.let { cat ->
        var name by remember(cat.id) { mutableStateOf(cat.name) }
        var desc by remember(cat.id) { mutableStateOf(cat.description ?: "") }
        var sort by remember(cat.id) { mutableStateOf(cat.sortOrder.toString()) }
        var active by remember(cat.id) { mutableStateOf(cat.isActive) }
        FormDialog("تعديل القسم", busy, name.isNotBlank(), { editing = null }, {
            action.run(toast, { editing = null; toast("تم الحفظ"); vm.load(silent = true) }) {
                c.admin.updateCategory(cat.copy(name = name.trim(), description = desc.trim().ifBlank { null },
                    sortOrder = sort.toIntOrNull() ?: 0, isActive = active))
            }
        }) {
            MedkTextField(name, { name = it }, "الاسم")
            MedkTextField(desc, { desc = it }, "الوصف", singleLine = false, minLines = 2)
            MedkTextField(sort, { sort = it.filter(Char::isDigit) }, "الترتيب", keyboardType = KeyboardType.Number)
            SwitchRow("ظاهر للعملاء", active, { active = it })
        }
    }
}

// =============================================================== sections
@Composable
fun AdminSectionsScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-sections") {
        coroutineScope {
            val cats = async { c.catalog.categories(force = true) }
            val secs = async { c.catalog.allSections(force = true) }
            val staff = async { c.admin.staff() }
            Triple(cats.await(), secs.await(), staff.await())
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Section?>(null) }
    var creating by remember { mutableStateOf(false) }
    val action = actionVm("section-save")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    ManageScaffold("الأقسام الفرعية", nav, "قسم جديد", { creating = true }) {
        StateView(state, { vm.load() }) { (cats, sections, staff) ->
            val staffNames = staff.associate { it.id to it.displayName }
            LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { cat ->
                    item(key = "h" + cat.id) { Text(cat.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    val list = sections.filter { it.categoryId == cat.id }
                    if (list.isEmpty()) item(key = "e" + cat.id) {
                        Text("لا توجد أقسام فرعية", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(list, key = { it.id }) { s ->
                        MedkCard(onClick = { editing = s }, padding = PaddingValues(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(MedkShapes.Small)) {
                                    NetImage(s.imageUrl, Modifier.fillMaxSize(), palette = categoryPalette(cat.colorKey ?: cat.slug),
                                        placeholderIcon = categoryIcon(cat.iconKey))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                                    Text("البائع: " + (s.assignedStaffId?.let { staffNames[it] } ?: "غير مُسند"),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!s.isActive) Pill("مخفي", MedkColors.Danger)
                                Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            val target = editing
            if (target != null || creating) {
                var categoryId by remember(target?.id) { mutableStateOf(target?.categoryId ?: cats.firstOrNull()?.id ?: "") }
                var name by remember(target?.id) { mutableStateOf(target?.name ?: "") }
                var desc by remember(target?.id) { mutableStateOf(target?.description ?: "") }
                var image by remember(target?.id) { mutableStateOf(target?.imageUrl) }
                var staffId by remember(target?.id) { mutableStateOf(target?.assignedStaffId) }
                var sort by remember(target?.id) { mutableStateOf((target?.sortOrder ?: 0).toString()) }
                var active by remember(target?.id) { mutableStateOf(target?.isActive ?: true) }
                var confirmDelete by remember { mutableStateOf(false) }
                val close = { editing = null; creating = false }
                FormDialog(if (target == null) "قسم فرعي جديد" else "تعديل القسم الفرعي", busy, name.isNotBlank() && categoryId.isNotEmpty(), close, {
                    action.run(toast, { close(); toast("تم الحفظ"); vm.load(silent = true) }) {
                        c.admin.saveSection(target?.id, SectionInput(categoryId, name.trim(), desc.trim().ifBlank { null }, image,
                            staffId, sort.toIntOrNull() ?: 0, active))
                    }
                }, onDelete = target?.let { { confirmDelete = true } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ImageUploadBox(image, { image = it }, 80.dp, folder = "sections")
                        Spacer(Modifier.width(10.dp))
                        Text("صورة اللعبة / التطبيق", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownField("القسم الرئيسي", cats.map { it.id to it.name }, categoryId, { categoryId = it })
                    MedkTextField(name, { name = it }, "الاسم (مثال: Free Fire)")
                    MedkTextField(desc, { desc = it }, "وصف قصير")
                    DropdownField("البائع المسؤول", listOf<Pair<String?, String>>(null to "غير مُسند") +
                        staff.filter { it.isActive }.map { it.id as String? to it.displayName }, staffId, { staffId = it })
                    MedkTextField(sort, { sort = it.filter(Char::isDigit) }, "الترتيب", keyboardType = KeyboardType.Number)
                    SwitchRow("ظاهر للعملاء", active, { active = it })
                }
                if (confirmDelete && target != null) AlertDialog({ confirmDelete = false }, title = { Text("حذف ${target.name}؟") },
                    text = { Text("ستبقى المنتجات لكن بدون قسم فرعي.") },
                    confirmButton = { TextButton({
                        confirmDelete = false
                        action.run(toast, { close(); vm.load(silent = true) }) { c.admin.deleteSection(target.id) }
                    }) { Text("حذف", color = MedkColors.Danger) } },
                    dismissButton = { TextButton({ confirmDelete = false }) { Text("إلغاء") } })
            }
        }
    }
}

// ================================================================== staff
@Composable
fun AdminStaffScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-staff") { c.admin.staff() }
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val action = actionVm("staff-create")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    ManageScaffold("البائعون والصلاحيات", nav, "بائع جديد", { creating = true }) {
        StateView(state, { vm.load() }) { list ->
            if (list.isEmpty()) EmptyBox(Icons.Filled.Groups, "لا يوجد بائعون")
            else LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { p ->
                    MedkCard(onClick = if (p.roleEnum == Role.STAFF) ({ nav.navigate(Routes.staffDetail(p.id)) }) else null, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.displayName, p.avatarUrl, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(p.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (p.roleEnum == Role.STAFF) Text(permissionSummary(p), style = MaterialTheme.typography.labelSmall, color = MedkColors.Violet)
                            }
                            when {
                                p.roleEnum == Role.OWNER -> Pill("المالك", MedkColors.Violet)
                                p.isActive -> Pill("نشط", MedkColors.Success, icon = Icons.Filled.CheckCircle)
                                else -> Pill("موقوف", MedkColors.Danger, icon = Icons.Filled.Block)
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var perms by remember { mutableStateOf(StaffPermissions()) }
        FormDialog("إضافة بائع", busy, name.isNotBlank() && email.contains('@') && pass.length >= 8, { creating = false }, {
            action.run(toast, { creating = false; toast("تم إنشاء حساب البائع"); vm.load(silent = true) }) {
                c.admin.createStaff(name.trim(), email.trim(), pass, perms)
            }
        }) {
            MedkTextField(name, { name = it }, "الاسم الكامل", leading = Icons.Filled.Person)
            MedkTextField(email, { email = it }, "البريد الإلكتروني", leading = Icons.Filled.Email, keyboardType = KeyboardType.Email)
            MedkTextField(pass, { pass = it }, "كلمة المرور (8 أحرف على الأقل)", leading = Icons.Filled.Lock, isPassword = true)
            PermissionSwitches(perms) { perms = it }
        }
    }
}

@Composable
fun StaffDetailScreen(id: String, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("staff-$id") {
        coroutineScope {
            val p = async { c.admin.staff().first { it.id == id } }
            val cats = async { c.catalog.categories(force = true) }
            val secs = async { c.catalog.allSections(force = true) }
            val mine = async { c.admin.staffSections(id) }
            val orders = async { runCatching { c.orders.visibleOrders() }.getOrDefault(emptyList()).filter { it.staffId == id } }
            StaffDetail(p.await(), cats.await(), secs.await(), mine.await(), orders.await())
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val action = actionVm("staff-detail")
    val busy by action.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar("صلاحيات البائع", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { d ->
            var perms by remember(d.profile) { mutableStateOf(d.profile.permissions) }
            val selected = remember(d.sections) { mutableStateListOf<String>().apply { addAll(d.sections) } }
            val assigned = d.allSections.filter { it.assignedStaffId == id }.map { it.id }.toSet()
            var newPass by rememberSaveable { mutableStateOf("") }
            var confirmToggle by remember { mutableStateOf(false) }
            var confirmDemote by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MedkCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(d.profile.displayName, d.profile.avatarUrl, 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.profile.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(d.profile.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(if (d.profile.isActive) "نشط" else "موقوف", if (d.profile.isActive) MedkColors.Success else MedkColors.Danger)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Row {
                            MiniStat("طلبات مسندة", "${d.orders.size}", Modifier.weight(1f))
                            MiniStat("مفتوحة", "${d.orders.count { it.status == "open" }}", Modifier.weight(1f))
                            MiniStat("مسلّمة", "${d.orders.count { it.status == "delivered" || it.status == "closed" }}", Modifier.weight(1f))
                        }
                    }
                }
                Text("الصلاحيات", style = MaterialTheme.typography.titleMedium)
                MedkCard { PermissionSwitches(perms) { perms = it } }
                Text("الأقسام والألعاب المسموحة", style = MaterialTheme.typography.titleMedium)
                Text("يرى البائع طلبات ومحادثات الأقسام المسندة إليه، ويدير منتجات الأقسام المحددة هنا.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MedkCard(padding = PaddingValues(8.dp)) {
                    Column {
                        d.categories.forEach { cat ->
                            val secs = d.allSections.filter { it.categoryId == cat.id }
                            if (secs.isNotEmpty()) {
                                Text(cat.name, style = MaterialTheme.typography.labelLarge, color = MedkColors.Violet, modifier = Modifier.padding(8.dp))
                                secs.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(s.id in selected, { on -> if (on) selected.add(s.id) else selected.remove(s.id) },
                                            colors = CheckboxDefaults.colors(checkedColor = MedkColors.Purple))
                                        Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        if (s.id in assigned) Pill("البائع المسؤول", MedkColors.Info)
                                    }
                                }
                            }
                        }
                    }
                }
                PrimaryButton("حفظ الصلاحيات", {
                    action.run(toast, { toast("تم حفظ الصلاحيات"); vm.load(silent = true) }) { c.admin.setPermissions(id, perms, selected.toSet()) }
                }, loading = busy)
                Text("الأمان", style = MaterialTheme.typography.titleMedium)
                MedkTextField(newPass, { newPass = it }, "كلمة مرور جديدة (8 أحرف على الأقل)", leading = Icons.Filled.Lock, isPassword = true)
                SecondaryButton("تعيين كلمة المرور", {
                    action.run(toast, { toast("تم تغيير كلمة المرور وتسجيل خروج البائع من أجهزته"); newPass = "" }) { c.admin.setStaffPassword(id, newPass) }
                }, enabled = newPass.length >= 8 && !busy)
                SecondaryButton(if (d.profile.isActive) "إيقاف الحساب" else "تفعيل الحساب", { confirmToggle = true },
                    color = if (d.profile.isActive) MedkColors.Danger else MedkColors.Success, enabled = !busy)
                SecondaryButton("إزالة صفة البائع (تحويله إلى عميل)", { confirmDemote = true }, color = MedkColors.Danger, enabled = !busy)
                Spacer(Modifier.height(16.dp))
            }
            if (confirmToggle) AlertDialog({ confirmToggle = false },
                title = { Text(if (d.profile.isActive) "إيقاف البائع؟" else "تفعيل البائع؟") },
                text = { Text(if (d.profile.isActive) "لن يتمكن من تسجيل الدخول وسيتم إنهاء جلساته." else "سيتمكن من تسجيل الدخول مجدداً.") },
                confirmButton = { TextButton({
                    confirmToggle = false
                    action.run(toast, { toast("تم التحديث"); vm.load(silent = true) }) { c.admin.setUserActive(id, !d.profile.isActive) }
                }) { Text("تأكيد", color = MedkColors.Violet) } },
                dismissButton = { TextButton({ confirmToggle = false }) { Text("إلغاء") } })
            if (confirmDemote) AlertDialog({ confirmDemote = false },
                title = { Text("تحويل إلى عميل؟") },
                text = { Text("سيفقد ${d.profile.displayName} كل صلاحيات البائع. الطلبات المسندة إليه تبقى في السجل.") },
                confirmButton = { TextButton({
                    confirmDemote = false
                    action.run(toast, { toast("تم التحويل إلى عميل"); nav.popBackStack() }) { c.admin.setRole(id, Role.CUSTOMER) }
                }) { Text("تحويل", color = MedkColors.Danger) } },
                dismissButton = { TextButton({ confirmDemote = false }) { Text("إلغاء") } })
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The four seller permission switches (shared by "create" and "edit"). */
@Composable
fun PermissionSwitches(perms: StaffPermissions, onChange: (StaffPermissions) -> Unit) {
    Column {
        SwitchRow("إدارة المنتجات", perms.manageProducts, { onChange(perms.copy(manageProducts = it)) }, "إضافة وتعديل وحذف المنتجات في الأقسام المسموحة")
        HorizontalDivider()
        SwitchRow("تحديث حالة الطلبات", perms.updateOrders, { onChange(perms.copy(updateOrders = it)) }, "تأكيد الدفع والتسليم وإغلاق الطلبات المسندة إليه")
        HorizontalDivider()
        SwitchRow("إدارة أسعار P2P", perms.manageP2p, { onChange(perms.copy(manageP2p = it)) }, "تعديل أسعار شراء وبيع العملات الرقمية")
        HorizontalDivider()
        SwitchRow("عرض الإحصائيات والإيرادات", perms.viewStats, { onChange(perms.copy(viewStats = it)) }, "رؤية أرقام المبيعات الخاصة بطلباته")
    }
}

fun permissionSummary(p: Profile): String = listOfNotNull(
    "المنتجات".takeIf { p.canManageProducts }, "الطلبات".takeIf { p.canUpdateOrders },
    "P2P".takeIf { p.canManageP2p }, "الإحصائيات".takeIf { p.canViewStats },
).joinToString(" • ").ifEmpty { "بدون صلاحيات إضافية" }

data class StaffDetail(val profile: Profile, val categories: List<Category>, val allSections: List<Section>, val sections: Set<String>,
                       val orders: List<com.medk.app.data.Order>)

// ================================================================ banners
@Composable
fun AdminBannersScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-banners") { c.admin.allBanners() to c.catalog.categories(force = true) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Banner?>(null) }
    var creating by remember { mutableStateOf(false) }
    val action = actionVm("banner-save")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    ManageScaffold("البانرات الإعلانية", nav, "بانر جديد", { creating = true }) {
        StateView(state, { vm.load() }) { (banners, cats) ->
            if (banners.isEmpty()) EmptyBox(Icons.Filled.ViewCarousel, "لا توجد بانرات", "يظهر بانر الترحيب الافتراضي في الصفحة الرئيسية")
            else LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(banners, key = { it.id }) { b ->
                    MedkCard(onClick = { editing = b }, padding = PaddingValues(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 96.dp, height = 56.dp).clip(MedkShapes.Small)) { NetImage(b.imageUrl, Modifier.fillMaxSize()) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.titleSmall)
                                Text(b.subtitle ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (!b.isActive) Pill("مخفي", MedkColors.Danger)
                        }
                    }
                }
            }
            val target = editing
            if (target != null || creating) {
                var title by remember(target?.id) { mutableStateOf(target?.title ?: "") }
                var subtitle by remember(target?.id) { mutableStateOf(target?.subtitle ?: "") }
                var image by remember(target?.id) { mutableStateOf(target?.imageUrl) }
                var slug by remember(target?.id) { mutableStateOf(target?.categorySlug) }
                var sort by remember(target?.id) { mutableStateOf((target?.sortOrder ?: 0).toString()) }
                var active by remember(target?.id) { mutableStateOf(target?.isActive ?: true) }
                val close = { editing = null; creating = false }
                FormDialog(if (target == null) "بانر جديد" else "تعديل البانر", busy, title.isNotBlank(), close, {
                    action.run(toast, { close(); vm.load(silent = true) }) {
                        c.admin.saveBanner(target?.id, BannerInput(title.trim(), subtitle.trim().ifBlank { null }, image, slug, sort.toIntOrNull() ?: 0, active))
                    }
                }, onDelete = target?.let { { action.run(toast, { close(); vm.load(silent = true) }) { c.admin.deleteBanner(it.id) } } }) {
                    ImageUploadBox(image, { image = it }, 110.dp, folder = "banners", label = "صورة البانر")
                    MedkTextField(title, { title = it }, "العنوان")
                    MedkTextField(subtitle, { subtitle = it }, "النص الفرعي")
                    DropdownField("يفتح القسم", listOf<Pair<String?, String>>(null to "كل الأقسام") + cats.map { it.slug as String? to it.name }, slug, { slug = it })
                    MedkTextField(sort, { sort = it.filter(Char::isDigit) }, "الترتيب", keyboardType = KeyboardType.Number)
                    SwitchRow("ظاهر", active, { active = it })
                }
            }
        }
    }
}

// ==================================================================== p2p
@Composable
fun AdminP2pScreen(nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("admin-p2p") { c.catalog.p2pAssets() }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<P2pAsset?>(null) }
    var creating by remember { mutableStateOf(false) }
    val action = actionVm("p2p-save")
    val busy by action.busy.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    ManageScaffold("أسعار العملات P2P", nav, "عملة جديدة", { creating = true }) {
        StateView(state, { vm.load() }) { list ->
            if (list.isEmpty()) EmptyBox(Icons.Filled.CurrencyExchange, "لا توجد عملات", "أضف USDT أو أي عملة تدعمها مع أسعار الشراء والبيع")
            else LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { a ->
                    MedkCard(onClick = { editing = a }, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.code} — ${a.name}", style = MaterialTheme.typography.titleSmall)
                                Text(a.network ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("شراء ${dzd(a.buyPriceDzd)}", style = MaterialTheme.typography.labelMedium, color = MedkColors.Success)
                                Text("بيع ${dzd(a.sellPriceDzd)}", style = MaterialTheme.typography.labelMedium, color = MedkColors.Danger)
                            }
                            if (!a.isActive) Pill("مخفي", MedkColors.Danger, Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
    val target = editing
    if (target != null || creating) {
        var code by remember(target?.id) { mutableStateOf(target?.code ?: "USDT") }
        var name by remember(target?.id) { mutableStateOf(target?.name ?: "Tether") }
        var network by remember(target?.id) { mutableStateOf(target?.network ?: "TRC20") }
        var buy by remember(target?.id) { mutableStateOf(target?.buyPriceDzd?.plain() ?: "") }
        var sell by remember(target?.id) { mutableStateOf(target?.sellPriceDzd?.plain() ?: "") }
        var min by remember(target?.id) { mutableStateOf(target?.minAmount?.plain() ?: "") }
        var max by remember(target?.id) { mutableStateOf(target?.maxAmount?.plain() ?: "") }
        var active by remember(target?.id) { mutableStateOf(target?.isActive ?: true) }
        val close = { editing = null; creating = false }
        FormDialog(if (target == null) "عملة جديدة" else "تعديل ${target.code}", busy,
            code.isNotBlank() && name.isNotBlank() && buy.toPriceOrNull() != null && sell.toPriceOrNull() != null, close, {
                action.run(toast, { close(); vm.load(silent = true) }) {
                    c.admin.saveP2p(target?.id, P2pAssetInput(code.trim().uppercase(), name.trim(), network.trim().ifBlank { null },
                        buy.toPriceOrNull()!!, sell.toPriceOrNull()!!, min.toPriceOrNull(), max.toPriceOrNull(), active))
                }
            }, onDelete = target?.let { { action.run(toast, { close(); vm.load(silent = true) }) { c.admin.deleteP2p(it.id) } } }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedkTextField(code, { code = it }, "الرمز", Modifier.weight(1f))
                MedkTextField(name, { name = it }, "الاسم", Modifier.weight(1f))
            }
            MedkTextField(network, { network = it }, "الشبكة (TRC20, BEP20…)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedkTextField(buy, { buy = it }, "سعر الشراء DZD", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                MedkTextField(sell, { sell = it }, "سعر البيع DZD", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedkTextField(min, { min = it }, "الحد الأدنى", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                MedkTextField(max, { max = it }, "الحد الأقصى", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            }
            SwitchRow("ظاهر للعملاء", active, { active = it })
        }
    }
}

