package com.medk.app.data.demo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.medk.app.core.AppContainer
import com.medk.app.data.AdminRepository
import com.medk.app.data.AppNotification
import com.medk.app.data.AuthRepository
import com.medk.app.data.AuthState
import com.medk.app.data.Banner
import com.medk.app.data.BannerInput
import com.medk.app.data.CartLine
import com.medk.app.data.CatalogRepository
import com.medk.app.data.Category
import com.medk.app.data.ChatRepository
import com.medk.app.data.Conversation
import com.medk.app.data.CreatedOrder
import com.medk.app.data.DashboardStats
import com.medk.app.data.Message
import com.medk.app.data.NotificationRepository
import com.medk.app.data.Order
import com.medk.app.data.OrderItem
import com.medk.app.data.OrderRepository
import com.medk.app.data.OrderStatus
import com.medk.app.data.P2pAsset
import com.medk.app.data.P2pAssetInput
import com.medk.app.data.Product
import com.medk.app.data.ProductInput
import com.medk.app.data.Profile
import com.medk.app.data.Role
import com.medk.app.data.Section
import com.medk.app.data.SectionInput
import com.medk.app.data.StaffPermissions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.medk.app.data.demo.DemoStore as S

private suspend fun net() = delay(250)
private fun me() = S.currentUid?.let { S.profiles[it] } ?: error("not signed in")
private fun isOwner() = me().roleEnum == Role.OWNER
private fun Profile.seesOrder(o: Order) = roleEnum == Role.OWNER || o.customerId == id || o.staffId == id
private fun Profile.seesConv(c: Conversation) = roleEnum == Role.OWNER || c.customerId == id || c.staffId == id

// ================================================================== auth
class DemoAuthRepository(private val c: AppContainer) : AuthRepository(c) {
    init { _state.value = AuthState.SignedOut }

    override val currentUid: String? get() = S.currentUid

    fun loginAs(role: Role) {
        val uid = when (role) { Role.CUSTOMER -> S.CUSTOMER; Role.STAFF -> S.STAFF_GAMES; Role.OWNER -> S.OWNER }
        S.currentUid = uid
        c.notifications.start(uid)
        _state.value = AuthState.SignedIn(S.profiles.getValue(uid))
    }

    override suspend fun restore() { _state.value = S.currentUid?.let { AuthState.SignedIn(S.profiles.getValue(it)) } ?: AuthState.SignedOut }

    override suspend fun signIn(email: String, password: String) {
        net()
        val p = S.profiles.values.firstOrNull { it.email.equals(email.trim(), true) }
        if (p != null) { if (!p.isActive) error("account_disabled"); S.currentUid = p.id; c.notifications.start(p.id); _state.value = AuthState.SignedIn(p) }
        else loginAs(Role.CUSTOMER)
    }

    override suspend fun register(name: String, email: String, password: String) {
        net()
        if (S.profiles.values.any { it.email.equals(email.trim(), true) }) error("email already in use")
        val p = Profile("u_" + S.id().take(8), email.trim(), name.trim(), role = "customer", createdAt = S.now())
        S.profiles[p.id] = p; S.currentUid = p.id; c.notifications.start(p.id)
        _state.value = AuthState.SignedIn(p)
    }

    override suspend fun sendPasswordReset(email: String) { net() }
    override suspend fun signInWithGoogle(activityContext: Context) { net(); loginAs(Role.CUSTOMER) }
    override suspend fun refreshProfile() { S.currentUid?.let { _state.value = AuthState.SignedIn(S.profiles.getValue(it)) } }
    override suspend fun updateName(name: String) {
        val p = me(); S.profiles[p.id] = p.copy(fullName = name); refreshProfile()
    }
    override val isPasswordAccount: Boolean get() = true
    override suspend fun ensureEmailVerified() {}
    override suspend fun changePassword(current: String, newPassword: String) { net() }
    override suspend fun signOut(context: Context) {
        c.notifications.stop(); S.currentUid = null; c.cart.clear(); _state.value = AuthState.SignedOut
    }
}

// =============================================================== catalog
class DemoCatalogRepository(c: AppContainer) : CatalogRepository(c) {
    private fun visible(p: Product) = p.isAvailable || (S.currentUid?.let { S.profiles[it]?.roleEnum } ?: Role.CUSTOMER) != Role.CUSTOMER
    override suspend fun categories(force: Boolean): List<Category> { net(); return S.categories.sortedBy { it.sortOrder } }
    override suspend fun allSections(force: Boolean): List<Section> = S.sections.sortedBy { it.sortOrder }
    override fun invalidate() {}
    override suspend fun productsBySection(sectionId: String) = run { net(); S.products.filter { it.sectionId == sectionId && visible(it) } }
    override suspend fun productsByCategory(categoryId: String) = run { net(); S.products.filter { it.categoryId == categoryId && visible(it) } }
    override suspend fun latestProducts(limit: Long) = S.products.filter { it.isAvailable }.sortedByDescending { it.createdAt }.take(limit.toInt())
    override suspend fun allProducts() = S.products.sortedByDescending { it.createdAt }
    override suspend fun product(id: String) = run { net(); S.products.firstOrNull { it.id == id } ?: error("المنتج غير موجود") }
    override suspend fun search(query: String): List<Product> {
        net(); val q = query.trim()
        return S.products.filter { visible(it) && (it.name.contains(q, true) || it.description.orEmpty().contains(q, true)) }
    }
    override suspend fun banners(): List<Banner> = S.banners.filter { it.isActive }.sortedBy { it.sortOrder }
    override suspend fun p2pAssets(): List<P2pAsset> = run { net(); S.p2p.sortedBy { it.sortOrder } }
    override suspend fun loadFavorites(): List<Product> {
        val ids = S.favorites[S.currentUid ?: return emptyList()].orEmpty()
        _favoriteIds.value = ids.toSet()
        return S.products.filter { it.id in ids }
    }
    override suspend fun toggleFavorite(productId: String) {
        val set = S.favorites.getOrPut(S.currentUid ?: return) { mutableSetOf() }
        if (!set.add(productId)) set.remove(productId)
        _favoriteIds.value = set.toSet()
    }
}

// ================================================================ orders
class DemoOrderRepository(c: AppContainer) : OrderRepository(c) {
    override suspend fun visibleOrders(status: OrderStatus?, onlyMine: Boolean): List<Order> {
        net(); val p = me()
        return S.orders.filter { p.seesOrder(it) && (status == null || it.status == status.key) && (!onlyMine || it.customerId == p.id) }
            .sortedByDescending { it.createdAt }
    }
    override suspend fun order(id: String) = run { net(); S.orders.first { it.id == id }.also { if (!me().seesOrder(it)) error("permission denied") } }

    override suspend fun updateStatus(orderId: String, status: OrderStatus) {
        net(); val p = me()
        val i = S.orders.indexOfFirst { it.id == orderId }
        val o = S.orders[i]
        if (!(p.roleEnum == Role.OWNER || (o.staffId == p.id && p.canUpdateOrders))) error("permission denied")
        val t = S.now()
        S.orders[i] = o.copy(status = status.key, updatedAt = t,
            paidAt = if (status != OrderStatus.OPEN) o.paidAt ?: t else o.paidAt,
            deliveredAt = if (status == OrderStatus.DELIVERED || status == OrderStatus.CLOSED) o.deliveredAt ?: t else o.deliveredAt,
            closedAt = if (status == OrderStatus.CLOSED) t else o.closedAt)
        S.notify(o.customerId, "تحديث الطلب ${o.reference}", "حالة طلبك الآن: ${status.label}", orderId = o.id)
    }

    override suspend fun assignStaff(orderId: String, staffId: String?) {
        if (!isOwner()) error("permission denied")
        val i = S.orders.indexOfFirst { it.id == orderId }
        S.orders[i] = S.orders[i].copy(staffId = staffId)
        S.conversations.replaceAll { if (it.orderId == orderId) it.copy(staffId = staffId) else it }
        S.notify(staffId, "طلب جديد مُسند إليك", "الطلب ${S.orders[i].reference}", orderId = orderId)
    }

    override suspend fun checkout(lines: List<CartLine>, note: String?): List<CreatedOrder> {
        delay(600); val uid = me().id
        val resolved = lines.mapNotNull { l -> S.products.firstOrNull { it.id == l.productId && it.isAvailable }?.let { it to l.quantity } }
        if (resolved.isEmpty()) error("no available products")
        if (resolved.any { (p, q) -> p.stock != null && p.stock < q }) error("insufficient stock")
        val sectionsById = S.sections.associateBy { it.id }
        return resolved.groupBy { (p, _) -> p.sectionId?.let { sectionsById[it]?.assignedStaffId } }.map { (staff, group) ->
            val oid = S.id(); val ref = "MK-" + S.id().replace("-", "").take(8).uppercase()
            val usd = group.sumOf { (p, q) -> p.priceUsd * q }; val dzd = group.sumOf { (p, q) -> p.priceDzd * q }
            val secIds = group.mapNotNull { it.first.sectionId }.distinct()
            S.orders += Order(oid, ref, uid, staff, secIds.singleOrNull(), "open", usd, dzd, note, S.now(), S.now(),
                items = group.map { (p, q) -> OrderItem(S.id(), oid, p.id, p.name, p.cover, q, p.priceUsd, p.priceDzd) })
            group.forEach { (p, q) -> if (p.stock != null) S.products.replaceAll { if (it.id == p.id) it.copy(stock = it.stock!! - q) else it } }
            val cid = S.id()
            val secNames = secIds.mapNotNull { sectionsById[it]?.name }.joinToString("، ")
            val body = "مرحباً، أرغب في إتمام الطلب $ref. الإجمالي: ${"%.2f".format(usd)} USD / ${dzd.toLong()} DZD"
            S.conversations += Conversation(cid, uid, staff, oid, null, secIds.singleOrNull(),
                "طلب $ref" + if (secNames.isNotEmpty()) " — $secNames" else "", body, S.now(), S.now())
            S.messages += Message(S.id(), cid, uid, body, createdAt = S.now())
            S.notify(staff, "طلب جديد", "الطلب $ref", cid, oid)
            S.profiles.values.filter { it.role == "owner" }.forEach { S.notify(it.id, "طلب جديد في المتجر", "الطلب $ref — ${"%.2f".format(usd)} USD", cid, oid) }
            CreatedOrder(oid, cid, ref)
        }
    }
}

// ================================================================== chat
class DemoChatRepository(private val c: AppContainer) : ChatRepository(c) {
    override suspend fun conversations() = run { net(); val p = me(); S.conversations.filter { p.seesConv(it) }.sortedByDescending { it.lastMessageAt } }
    override suspend fun conversation(id: String) = S.conversations.first { it.id == id }
    override suspend fun conversationForOrder(orderId: String) = S.conversations.firstOrNull { it.orderId == orderId }
    override suspend fun messages(conversationId: String) = run { net(); S.messages.filter { it.conversationId == conversationId }.sortedBy { it.createdAt } }
    override fun newMessages(conversationId: String): Flow<Message> = S.messageEvents.filter { it.conversationId == conversationId }

    private fun post(convId: String, sender: String, body: String?, path: String? = null, type: String? = null, name: String? = null): Message {
        val m = Message(S.id(), convId, sender, body, path, type, name, S.now())
        S.messages += m
        val preview = body?.takeIf { it.isNotBlank() } ?: when (type) { "image" -> "📷 صورة"; "video" -> "🎬 فيديو"; else -> "📄 ملف PDF" }
        S.conversations.replaceAll { if (it.id == convId) it.copy(lastMessage = preview, lastMessageAt = m.createdAt, lastSenderId = sender) else it }
        S.messageEvents.tryEmit(m)
        val conv = S.conversations.first { it.id == convId }
        val target = if (sender == conv.customerId) conv.staffId ?: S.OWNER else conv.customerId
        if (target != sender) S.notify(target, "رسالة جديدة", preview, convId)
        return m
    }

    /** Simulates a seller reply so the demo chat feels alive. */
    private fun autoReply(convId: String) {
        val conv = S.conversations.first { it.id == convId }
        if (S.currentUid != conv.customerId) return
        val replier = conv.staffId ?: S.OWNER
        c.appScope.launch {
            delay(1800)
            val n = S.messages.count { it.conversationId == convId && it.senderId == replier }
            val text = when {
                conv.orderId != null && n == 0 -> "أهلاً بك 👋 استلمت طلبك. أرسل معرّف اللاعب/الحساب وسأرسل لك تفاصيل الدفع."
                conv.orderId != null -> "تمام، تم تسجيل المعلومات ✅ سأقوم بتحديث حالة الطلب فور تأكيد الدفع."
                else -> "مرحباً! هذا رد تلقائي في النسخة التجريبية — في التطبيق الحقيقي يرد عليك موظف MED-K."
            }
            post(convId, replier, text)
        }
    }

    override suspend fun sendText(conversationId: String, text: String): Message {
        val m = post(conversationId, me().id, text.trim()); autoReply(conversationId); return m
    }

    override suspend fun sendAttachment(conversationId: String, uri: Uri, caption: String?): Message {
        delay(500)
        val mime = c.context.contentResolver.getType(uri) ?: ""
        val type = when { mime.startsWith("image/") -> "image"; mime.startsWith("video/") -> "video"; mime == "application/pdf" -> "pdf"; else -> error("نوع الملف غير مدعوم") }
        val name = c.context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        val m = post(conversationId, me().id, caption, uri.toString(), type, name); autoReply(conversationId); return m
    }

    override suspend fun attachmentUrl(path: String) = path

    override suspend fun start(productId: String?, sectionId: String?, subject: String?): String {
        net(); val uid = me().id
        val sec = productId?.let { id -> S.products.firstOrNull { it.id == id }?.sectionId } ?: sectionId
        S.conversations.firstOrNull { it.customerId == uid && it.orderId == null && it.productId == productId && it.sectionId == sec }?.let { return it.id }
        val staff = sec?.let { s -> S.sections.firstOrNull { it.id == s }?.assignedStaffId }
        val conv = Conversation(S.id(), uid, staff, null, productId, sec, subject ?: "الدعم الفني", null, S.now(), S.now())
        S.conversations += conv
        return conv.id
    }

    override suspend fun assignStaff(conversationId: String, staffId: String?) {
        S.conversations.replaceAll { if (it.id == conversationId) it.copy(staffId = staffId) else it }
    }

    override suspend fun profiles(ids: Collection<String>): Map<String, Profile> = ids.mapNotNull { id -> S.profiles[id]?.let { id to it } }.toMap()
}

// ================================================================= admin
class DemoAdminRepository(private val c: AppContainer) : AdminRepository(c) {
    override suspend fun invites(): List<com.medk.app.data.Invite> = emptyList()
    override suspend fun invite(email: String, name: String?, perms: com.medk.app.data.StaffPermissions, sectionIds: List<String>) =
        error("دعوة البائعين بالبريد متاحة في النسخة الحقيقية فقط")
    override suspend fun deleteInvite(email: String) {}

    override suspend fun stats(): DashboardStats {
        net(); val p = me()
        if (p.roleEnum != Role.OWNER && !p.canViewStats) error("forbidden")
        val o = S.orders.filter { p.roleEnum == Role.OWNER || it.staffId == p.id }
        val paidish = o.filter { it.status != "open" }
        return DashboardStats(o.size, o.count { it.status == "open" }, o.count { it.status == "paid" }, o.count { it.status == "delivered" },
            o.count { it.status == "closed" }, paidish.sumOf { it.totalUsd }, paidish.sumOf { it.totalDzd },
            S.conversations.count { c -> p.seesConv(c) && (c.orderId == null || S.orders.firstOrNull { it.id == c.orderId }?.status != "closed") },
            o.count { it.staffId == null && it.status != "closed" }, S.products.size,
            S.profiles.values.count { it.role == "staff" }, S.profiles.values.count { it.role == "customer" })
    }
    override suspend fun staff() = run { net(); S.profiles.values.filter { it.role != "customer" }.sortedBy { it.createdAt } }
    override suspend fun createStaff(name: String, email: String, password: String, perms: StaffPermissions): Profile {
        net(); if (!isOwner()) error("owner only")
        if (S.profiles.values.any { it.email.equals(email, true) }) error("email already in use")
        val p = Profile("st_" + S.id().take(8), email, name, role = "staff", canManageProducts = perms.manageProducts,
            canUpdateOrders = perms.updateOrders, canManageP2p = perms.manageP2p, canViewStats = perms.viewStats, createdAt = S.now())
        S.profiles[p.id] = p; return p
    }
    override suspend fun setUserActive(uid: String, active: Boolean) { net(); if (!isOwner()) error("owner only"); S.profiles[uid] = S.profiles.getValue(uid).copy(isActive = active) }
    override suspend fun setRole(uid: String, role: Role) {
        net(); if (!isOwner() || uid == S.currentUid || role == Role.OWNER) error("permission denied")
        S.profiles[uid] = S.profiles.getValue(uid).copy(role = role.name.lowercase())
    }
    override suspend fun customers() = run { net(); S.profiles.values.filter { it.role == "customer" }.sortedByDescending { it.createdAt } }
    override suspend fun setPermissions(uid: String, perms: StaffPermissions, sectionIds: Set<String>) {
        net(); if (!isOwner()) error("owner only")
        S.profiles[uid] = S.profiles.getValue(uid).copy(canManageProducts = perms.manageProducts, canUpdateOrders = perms.updateOrders,
            canManageP2p = perms.manageP2p, canViewStats = perms.viewStats)
        S.staffSections[uid] = sectionIds.toMutableSet()
    }
    override suspend fun staffSections(uid: String): Set<String> = S.staffSections[uid].orEmpty().toSet()
    override suspend fun updateCategory(cat: Category) { net(); S.categories.replaceAll { if (it.id == cat.id) cat else it } }
    override suspend fun saveSection(id: String?, input: SectionInput) {
        net(); val s = Section(id ?: S.id(), input.categoryId, input.name, input.description, input.imageUrl, input.assignedStaffId, input.sortOrder, input.isActive)
        if (id == null) S.sections += s else S.sections.replaceAll { if (it.id == id) s else it }
    }
    override suspend fun deleteSection(id: String) { S.sections.removeAll { it.id == id }; S.products.replaceAll { if (it.sectionId == id) it.copy(sectionId = null) else it } }
    override suspend fun saveProduct(id: String?, input: ProductInput) {
        net(); val old = id?.let { i -> S.products.firstOrNull { it.id == i } }
        val p = Product(id ?: S.id(), input.categoryId, input.sectionId, input.name, input.description, input.priceUsd, input.priceDzd,
            input.stock, input.isAvailable, input.images, old?.createdAt ?: S.now())
        if (old == null) S.products += p else S.products.replaceAll { if (it.id == id) p else it }
    }
    override suspend fun deleteProduct(id: String) { S.products.removeAll { it.id == id } }
    override suspend fun saveBanner(id: String?, input: BannerInput) {
        val b = Banner(id ?: S.id(), input.title, input.subtitle, input.imageUrl, input.categorySlug, input.sortOrder, input.isActive)
        if (id == null) S.banners += b else S.banners.replaceAll { if (it.id == id) b else it }
    }
    override suspend fun allBanners() = S.banners.sortedBy { it.sortOrder }
    override suspend fun deleteBanner(id: String) { S.banners.removeAll { it.id == id } }
    override suspend fun saveP2p(id: String?, input: P2pAssetInput) {
        if (!isOwner() && !me().canManageP2p) error("permission denied")
        val a = P2pAsset(id ?: S.id(), input.code, input.name, input.network, input.buyPriceDzd, input.sellPriceDzd, input.minAmount, input.maxAmount,
            input.isActive, S.p2p.size, S.now())
        if (id == null) S.p2p += a else S.p2p.replaceAll { if (it.id == id) a.copy(sortOrder = it.sortOrder) else it }
    }
    override suspend fun deleteP2p(id: String) {
        if (!isOwner() && !me().canManageP2p) error("permission denied")
        S.p2p.removeAll { it.id == id }
    }
    override suspend fun uploadImage(uri: Uri, folder: String): String {
        delay(400)
        // keep read access to the picked image for the demo session
        runCatching { c.context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return uri.toString()
    }
}

// ========================================================= notifications
class DemoNotificationRepository(private val c: AppContainer) : NotificationRepository(c) {
    private var job: Job? = null
    override suspend fun list(): List<AppNotification> {
        val l = S.notifications.filter { it.userId == S.currentUid }
        _unread.value = l.count { !it.isRead }; return l
    }
    override suspend fun markAllRead() { S.notifications.replaceAll { if (it.userId == S.currentUid) it.copy(isRead = true) else it }; _unread.value = 0 }
    override suspend fun markRead(id: String) { S.notifications.replaceAll { if (it.id == id) it.copy(isRead = true) else it }; list() }
    override fun start(uid: String) {
        stop()
        _unread.value = S.notifications.count { it.userId == uid && !it.isRead }
        job = c.appScope.launch {
            S.notificationEvents.filter { it.userId == uid }.collect { n ->
                _unread.value += 1
                if (n.conversationId != null && n.conversationId == activeConversationId) return@collect
                if (c.settings.notificationsEnabled.first()) show(n)
            }
        }
    }
    override fun stop() { job?.cancel(); job = null; _unread.value = 0 }
}
