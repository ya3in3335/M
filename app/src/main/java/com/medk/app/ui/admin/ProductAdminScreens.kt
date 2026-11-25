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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.medk.app.MedkApp
import com.medk.app.core.friendly
import com.medk.app.data.Category
import com.medk.app.data.Product
import com.medk.app.data.ProductInput
import com.medk.app.data.Section
import com.medk.app.ui.components.EmptyBox
import com.medk.app.ui.components.LocalToast
import com.medk.app.ui.components.MedkCard
import com.medk.app.ui.components.MedkTextField
import com.medk.app.ui.components.MedkTopBar
import com.medk.app.ui.components.NetImage
import com.medk.app.ui.components.Pill
import com.medk.app.ui.components.PriceBlock
import com.medk.app.ui.components.PrimaryButton
import com.medk.app.ui.components.StateView
import com.medk.app.ui.components.actionVm
import com.medk.app.ui.components.loadVm
import com.medk.app.ui.navigation.Routes
import com.medk.app.ui.theme.MedkColors
import kotlinx.coroutines.launch
import com.medk.app.ui.theme.MedkShapes

data class ProductAdminData(val products: List<Product>, val categories: List<Category>, val sections: List<Section>, val allowed: Set<String>?)

private suspend fun loadAdminCatalog(): ProductAdminData {
    val c = MedkApp.container
    val me = c.auth.profile ?: error("not signed in")
    val allowed = c.admin.mySectionIds(me)
    val cats = c.catalog.categories(force = true)
    val sections = c.catalog.allSections(force = true)
    val products = c.catalog.allProducts().filter { allowed == null || (it.sectionId != null && it.sectionId in allowed) }
    return ProductAdminData(products, cats, sections, allowed)
}

@Composable
fun AdminProductsScreen(nav: NavHostController, asTab: Boolean = false) {
    val c = MedkApp.container
    val vm = loadVm("admin-products") { loadAdminCatalog() }
    LifecycleResumeEffect(Unit) { vm.load(silent = true); onPauseOrDispose { } }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Product?>(null) }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Box(Modifier.statusBarsPadding()) { MedkTopBar("إدارة المنتجات", onBack = if (asTab) null else ({ nav.popBackStack() })) } },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.productEdit()) }, shape = MedkShapes.ChamferSm, containerColor = MedkColors.Purple, contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("منتج جديد") })
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            StateView(state, { vm.load() }) { d ->
                val sectionNames = d.sections.associate { it.id to it.name }
                val list = d.products.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                MedkTextField(query, { query = it }, "بحث في المنتجات", Modifier.padding(horizontal = 16.dp), leading = Icons.Filled.Search)
                if (d.allowed != null && d.allowed.isEmpty()) EmptyBox(Icons.Filled.Inventory2, "لا توجد أقسام مسموحة",
                    "اطلب من المالك منحك صلاحية على أقسام محددة")
                else if (list.isEmpty()) EmptyBox(Icons.Filled.Inventory2, "لا توجد منتجات", "أضف منتجك الأول بالضغط على «منتج جديد»")
                else LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { p ->
                        MedkCard(onClick = { nav.navigate(Routes.productEdit(p.id)) }, padding = PaddingValues(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(64.dp).clip(MedkShapes.Small)) { NetImage(p.cover, Modifier.fillMaxSize()) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(p.sectionId?.let { sectionNames[it] } ?: "بدون قسم فرعي", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PriceBlock(p.priceUsd, p.priceDzd, Modifier.weight(1f))
                                        Pill(if (p.inStock) (p.stock?.let { "المخزون $it" } ?: "متوفر") else "غير متوفر",
                                            if (p.inStock) MedkColors.Success else MedkColors.Danger)
                                    }
                                }
                                Column {
                                    IconButton({ nav.navigate(Routes.productEdit(p.id)) }) { Icon(Icons.Filled.Edit, "تعديل") }
                                    IconButton({ toDelete = p }) { Icon(Icons.Filled.DeleteOutline, "حذف", tint = MedkColors.Danger) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog({ toDelete = null }, title = { Text("حذف المنتج؟") }, text = { Text("سيتم حذف «${p.name}» نهائياً. الطلبات السابقة تحتفظ بنسخة من الاسم والسعر.") },
            confirmButton = { TextButton({
                toDelete = null
                scope.launch { try { c.admin.deleteProduct(p.id); toast("تم الحذف"); vm.load(silent = true) } catch (e: Exception) { toast(e.friendly()) } }
            }) { Text("حذف", color = MedkColors.Danger) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("إلغاء") } })
    }
}

@Composable
fun ProductEditScreen(id: String?, nav: NavHostController) {
    val c = MedkApp.container
    val vm = loadVm("product-edit-$id") { loadAdminCatalog() to id?.let { c.catalog.product(it) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val action = actionVm("product-save")
    val busy by action.busy.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MedkTopBar(if (id == null) "منتج جديد" else "تعديل المنتج", onBack = { nav.popBackStack() })
        StateView(state, { vm.load() }) { (d, existing) ->
            val allowedSections = d.sections.filter { d.allowed == null || it.id in d.allowed }
            val allowedCats = d.categories.filter { cat -> d.allowed == null || allowedSections.any { it.categoryId == cat.id } }
            var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: allowedCats.firstOrNull()?.id) }
            var sectionId by rememberSaveable { mutableStateOf(existing?.sectionId) }
            var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
            var desc by rememberSaveable { mutableStateOf(existing?.description ?: "") }
            var priceUsd by rememberSaveable { mutableStateOf(existing?.priceUsd?.plain() ?: "") }
            var priceDzd by rememberSaveable { mutableStateOf(existing?.priceDzd?.plain() ?: "") }
            var stock by rememberSaveable { mutableStateOf(existing?.stock?.toString() ?: "") }
            var available by rememberSaveable { mutableStateOf(existing?.isAvailable ?: true) }
            val images = remember { mutableStateListOf<String>().apply { existing?.images?.let { addAll(it) } } }
            val sectionsForCat = allowedSections.filter { it.categoryId == categoryId }
            LaunchedEffect(categoryId) { if (sectionId != null && sectionsForCat.none { it.id == sectionId }) sectionId = null }
            val sectionRequired = d.allowed != null
            val valid = categoryId != null && name.trim().length >= 2 && priceUsd.toPriceOrNull() != null && priceDzd.toPriceOrNull() != null &&
                (stock.isBlank() || stock.toIntOrNull()?.let { it >= 0 } == true) && (!sectionRequired || sectionId != null)

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("صور المنتج", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images.toList()) { url ->
                        Box {
                            Box(Modifier.size(96.dp).clip(MedkShapes.Card)) { NetImage(url, Modifier.fillMaxSize()) }
                            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(.6f)),
                                contentAlignment = Alignment.Center) {
                                IconButton({ images.remove(url) }, Modifier.size(26.dp)) { Icon(Icons.Filled.Close, "إزالة", tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                    item { ImageUploadBox(null, { images.add(it) }, label = "إضافة صورة") }
                }
                DropdownField("القسم الرئيسي", allowedCats.map { it.id as String? to it.name }, categoryId, { categoryId = it })
                DropdownField(if (sectionRequired) "القسم الفرعي (مطلوب)" else "القسم الفرعي / اللعبة",
                    (if (sectionRequired) emptyList() else listOf<Pair<String?, String>>(null to "بدون")) + sectionsForCat.map { it.id as String? to it.name },
                    sectionId, { sectionId = it })
                MedkTextField(name, { name = it }, "اسم المنتج")
                MedkTextField(desc, { desc = it }, "الوصف", singleLine = false, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedkTextField(priceUsd, { priceUsd = it }, "السعر USD", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    MedkTextField(priceDzd, { priceDzd = it }, "السعر DZD", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                }
                MedkTextField(stock, { stock = it.filter(Char::isDigit) }, "المخزون (اتركه فارغاً لغير محدود)", keyboardType = KeyboardType.Number)
                SwitchRow("متاح للبيع", available, { available = it }, "إخفاء المنتج عن العملاء عند الإيقاف")
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                PrimaryButton("حفظ المنتج", {
                    action.run(toast, { toast("تم حفظ المنتج"); nav.popBackStack() }) {
                        c.admin.saveProduct(existing?.id, ProductInput(
                            categoryId = categoryId!!, sectionId = sectionId, name = name.trim(), description = desc.trim().ifBlank { null },
                            priceUsd = priceUsd.toPriceOrNull()!!, priceDzd = priceDzd.toPriceOrNull()!!, stock = stock.toIntOrNull(),
                            isAvailable = available, images = images.toList()))
                    }
                }, enabled = valid, loading = busy)
            }
        }
    }
}

