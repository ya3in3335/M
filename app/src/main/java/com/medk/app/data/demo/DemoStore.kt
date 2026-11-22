package com.medk.app.data.demo

import com.medk.app.data.AppNotification
import com.medk.app.data.Banner
import com.medk.app.data.Category
import com.medk.app.data.Conversation
import com.medk.app.data.Message
import com.medk.app.data.Order
import com.medk.app.data.OrderItem
import com.medk.app.data.P2pAsset
import com.medk.app.data.Product
import com.medk.app.data.Profile
import com.medk.app.data.Section
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory backend used ONLY by the "demo" build flavor.
 * Everything lives in RAM and resets when the app process restarts.
 */
object DemoStore {
    fun now(minusMinutes: Long = 0): String = OffsetDateTime.now().minusMinutes(minusMinutes).toString()
    fun id(): String = UUID.randomUUID().toString()

    const val CUSTOMER = "demo_customer"
    const val STAFF_GAMES = "demo_staff_games"
    const val STAFF_APPS = "demo_staff_apps"
    const val OWNER = "demo_owner"

    var currentUid: String? = null

    val profiles = mutableMapOf(
        CUSTOMER to Profile(CUSTOMER, "customer@demo.medk", "عميل تجريبي", role = "customer", createdAt = now(9000)),
        STAFF_GAMES to Profile(STAFF_GAMES, "games@demo.medk", "أمين — بائع الألعاب", role = "staff", canManageProducts = true, createdAt = now(8000)),
        STAFF_APPS to Profile(STAFF_APPS, "apps@demo.medk", "سارة — بائعة الاشتراكات", role = "staff", canManageProducts = false, createdAt = now(7000)),
        OWNER to Profile(OWNER, "owner@demo.medk", "مالك MED-K", role = "owner", canManageProducts = true, createdAt = now(10000)),
    )

    val categories = mutableListOf(
        Category("c_gaming", "gaming", "شحن الألعاب", "جواهر - شدات - عملات داخل الألعاب", "orange", "gaming", 1),
        Category("c_subs", "subscriptions", "اشتراكات التطبيقات", "Netflix - YouTube - Spotify وأكثر", "blue", "subscriptions", 2),
        Category("c_acc", "accounts", "شراء الحسابات", "حسابات الألعاب والتطبيقات", "purple", "accounts", 3),
        Category("c_p2p", "p2p", "شراء وبيع العملات الرقمية P2P", "USDT والعملات الرقمية المدعومة", "green", "p2p", 4),
        Category("c_other", "other", "خدمات أخرى", "بطاقات الهدايا، تراخيص البرامج، VPN والخدمات الرقمية المتنوعة", "cyan", "other", 5),
    )

    val sections = mutableListOf(
        Section("s_ff", "c_gaming", "Free Fire", "جواهر فري فاير", assignedStaffId = STAFF_GAMES, sortOrder = 1),
        Section("s_pubg", "c_gaming", "PUBG Mobile", "شدات ببجي UC", assignedStaffId = STAFF_GAMES, sortOrder = 2),
        Section("s_cod", "c_gaming", "Call of Duty Mobile", "نقاط CP", assignedStaffId = STAFF_GAMES, sortOrder = 3),
        Section("s_roblox", "c_gaming", "Roblox", "روبكس", assignedStaffId = STAFF_GAMES, sortOrder = 4),
        Section("s_games_other", "c_gaming", "ألعاب أخرى", sortOrder = 5),
        Section("s_netflix", "c_subs", "Netflix", "اشتراكات نتفليكس", assignedStaffId = STAFF_APPS, sortOrder = 1),
        Section("s_yt", "c_subs", "YouTube Premium", assignedStaffId = STAFF_APPS, sortOrder = 2),
        Section("s_spotify", "c_subs", "Spotify", assignedStaffId = STAFF_APPS, sortOrder = 3),
        Section("s_apps_other", "c_subs", "تطبيقات أخرى", assignedStaffId = STAFF_APPS, sortOrder = 4),
        Section("s_acc_games", "c_acc", "حسابات الألعاب", assignedStaffId = STAFF_GAMES, sortOrder = 1),
        Section("s_acc_apps", "c_acc", "حسابات التطبيقات", assignedStaffId = STAFF_APPS, sortOrder = 2),
        Section("s_p2p", "c_p2p", "العملات الرقمية", assignedStaffId = STAFF_APPS, sortOrder = 1),
        Section("s_gift", "c_other", "بطاقات الهدايا", sortOrder = 1),
        Section("s_gplay", "c_other", "Google Play", sortOrder = 2),
        Section("s_apple", "c_other", "Apple Gift Card", sortOrder = 3),
        Section("s_lic", "c_other", "تراخيص البرامج", sortOrder = 4),
        Section("s_vpn", "c_other", "اشتراكات VPN", sortOrder = 5),
    )

    private fun p(id: String, cat: String, sec: String, name: String, desc: String, usd: Double, dzd: Double, stock: Int? = null, mins: Long = 100) =
        Product(id, cat, sec, name, desc, usd, dzd, stock, true, emptyList(), now(mins))

    val products = mutableListOf(
        p("p1", "c_gaming", "s_ff", "100 جوهرة Free Fire", "شحن مباشر عبر معرّف اللاعب (ID) خلال دقائق.", 1.0, 270.0, mins = 10),
        p("p2", "c_gaming", "s_ff", "520 جوهرة Free Fire", "شحن مباشر عبر ID. أرسل معرّفك في المحادثة بعد الطلب.", 5.0, 1350.0, mins = 20),
        p("p3", "c_gaming", "s_ff", "1080 جوهرة Free Fire", "أفضل قيمة — شحن مباشر.", 9.99, 2700.0, stock = 4, mins = 30),
        p("p4", "c_gaming", "s_pubg", "60 UC PUBG Mobile", "شدات ببجي موبايل عبر ID.", 0.99, 270.0, mins = 40),
        p("p5", "c_gaming", "s_pubg", "660 UC PUBG Mobile", "شدات ببجي موبايل عبر ID.", 9.5, 2550.0, mins = 50),
        p("p6", "c_gaming", "s_pubg", "1800 UC PUBG Mobile", "شدات ببجي موبايل عبر ID.", 24.0, 6500.0, stock = 6, mins = 60),
        p("p7", "c_gaming", "s_cod", "880 CP Call of Duty", "نقاط COD Mobile.", 9.99, 2700.0, mins = 70),
        p("p8", "c_gaming", "s_roblox", "800 Robux", "بطاقة روبكس رقمية.", 9.99, 2750.0, mins = 80),
        p("p9", "c_subs", "s_netflix", "Netflix Premium — شهر", "حساب مشترك 4K لمدة 30 يوماً.", 4.5, 1200.0, mins = 90),
        p("p10", "c_subs", "s_netflix", "Netflix Premium — 3 أشهر", "حساب مشترك 4K لمدة 90 يوماً.", 12.0, 3200.0, mins = 95),
        p("p11", "c_subs", "s_yt", "YouTube Premium — شهر", "بدون إعلانات + YouTube Music.", 3.0, 800.0),
        p("p12", "c_subs", "s_spotify", "Spotify Premium — شهر", "على حسابك الشخصي.", 3.5, 950.0),
        p("p13", "c_acc", "s_acc_games", "حساب Free Fire — مستوى 70", "سكنات نادرة + 3 حزم إليت. ربط بريد قابل للتغيير.", 35.0, 9500.0, stock = 1),
        p("p14", "c_acc", "s_acc_games", "حساب PUBG — Conqueror", "موسمين كونكر، أسلحة مطورة.", 60.0, 16000.0, stock = 1),
        p("p15", "c_acc", "s_acc_apps", "حساب ChatGPT Plus — شهر", "حساب جاهز لمدة 30 يوماً.", 15.0, 4000.0, stock = 3),
        p("p16", "c_other", "s_gplay", "Google Play — 10$", "بطاقة رقمية (الولايات المتحدة).", 10.5, 2850.0),
        p("p17", "c_other", "s_apple", "Apple Gift Card — 10$", "بطاقة آيتونز رقمية (US).", 10.8, 2900.0),
        p("p18", "c_other", "s_lic", "Windows 11 Pro — ترخيص", "مفتاح تفعيل أصلي مدى الحياة.", 8.0, 2200.0),
        p("p19", "c_other", "s_vpn", "NordVPN — 3 أشهر", "حساب VPN جاهز.", 7.0, 1900.0),
        p("p20", "c_other", "s_gift", "بطاقة PlayStation — 20$", "PSN US رقمية.", 21.0, 5700.0),
    )

    val p2p = mutableListOf(
        P2pAsset("a1", "USDT", "Tether", "TRC20", 255.0, 245.0, 10.0, 5000.0, updatedAt = now(30)),
        P2pAsset("a2", "BTC", "Bitcoin", "BTC", 16_500_000.0, 16_100_000.0, 0.0005, 0.5, sortOrder = 1, updatedAt = now(30)),
    )

    val banners = mutableListOf<Banner>()
    val staffSections = mutableMapOf(STAFF_GAMES to mutableSetOf("s_ff", "s_pubg", "s_cod", "s_roblox"), STAFF_APPS to mutableSetOf("s_netflix"))
    val favorites = mutableMapOf<String, MutableSet<String>>()
    val orders = mutableListOf<Order>()
    val conversations = mutableListOf<Conversation>()
    val messages = mutableListOf<Message>()
    val notifications = mutableListOf<AppNotification>()

    val messageEvents = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val notificationEvents = MutableSharedFlow<AppNotification>(extraBufferCapacity = 64)

    init { seedHistory() }

    /** One finished order + one conversation so lists aren't empty. */
    private fun seedHistory() {
        val prod = products.first { it.id == "p5" }
        val oid = "o_seed"
        val created = now(60 * 26)
        orders += Order(oid, "MK-DEMO0001", CUSTOMER, STAFF_GAMES, "s_pubg", "delivered", prod.priceUsd, prod.priceDzd, null,
            created, created, now(60 * 25), now(60 * 24), null,
            listOf(OrderItem(id(), oid, prod.id, prod.name, null, 1, prod.priceUsd, prod.priceDzd)))
        val cid = "conv_seed"
        conversations += Conversation(cid, CUSTOMER, STAFF_GAMES, oid, null, "s_pubg", "طلب MK-DEMO0001 — PUBG Mobile",
            "تم الشحن ✅ استمتع!", now(60 * 24), created)
        listOf(
            Triple(CUSTOMER, "مرحباً، أرغب في إتمام الطلب MK-DEMO0001", 60L * 26),
            Triple(STAFF_GAMES, "أهلاً بك! أرسل معرّف اللاعب (ID) من فضلك.", 60L * 26 - 3),
            Triple(CUSTOMER, "ID: 5123456789", 60L * 26 - 5),
            Triple(STAFF_GAMES, "تم استلام الدفع، جارٍ الشحن…", 60L * 25),
            Triple(STAFF_GAMES, "تم الشحن ✅ استمتع!", 60L * 24),
        ).forEach { (s, b, m) -> messages += Message(id(), cid, s, b, createdAt = now(m)) }
        notifications += AppNotification(id(), CUSTOMER, "تحديث الطلب MK-DEMO0001", "حالة طلبك الآن: تم التسليم", null, oid, false, now(60 * 24))
    }

    fun notify(userId: String?, title: String, body: String?, conversationId: String? = null, orderId: String? = null) {
        if (userId == null) return
        val n = AppNotification(id(), userId, title, body, conversationId, orderId, false, now())
        notifications.add(0, n)
        notificationEvents.tryEmit(n)
    }
}
