package com.medk.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.ZoneOffset

/** Firestore document → app model mapping (keeps the UI independent of the backend). */

internal fun Timestamp?.iso(): String? = this?.toDate()?.toInstant()?.atOffset(ZoneOffset.UTC)?.toString()
private fun DocumentSnapshot.ts(f: String): String? =
    getTimestamp(f, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).iso()
private fun DocumentSnapshot.num(f: String): Double = (get(f) as? Number)?.toDouble() ?: 0.0
private fun DocumentSnapshot.numOrNull(f: String): Double? = (get(f) as? Number)?.toDouble()
private fun DocumentSnapshot.int(f: String): Int = (get(f) as? Number)?.toInt() ?: 0
private fun DocumentSnapshot.bool(f: String, def: Boolean): Boolean = getBoolean(f) ?: def
@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.map(f: String): Map<String, Any?> = (get(f) as? Map<String, Any?>) ?: emptyMap()

fun DocumentSnapshot.toProfile(): Profile {
    val perms = map("perms")
    return Profile(
        id = id, email = getString("email"), fullName = getString("fullName"), avatarUrl = getString("avatarUrl"),
        role = getString("role") ?: "customer", isActive = bool("active", true),
        canManageProducts = perms["manageProducts"] as? Boolean ?: false,
        canUpdateOrders = perms["updateOrders"] as? Boolean ?: true,
        canManageP2p = perms["manageP2p"] as? Boolean ?: false,
        canViewStats = perms["viewStats"] as? Boolean ?: false,
        createdAt = ts("createdAt"),
    )
}

/** publicProfiles/{uid}: name + avatar + role only. */
fun DocumentSnapshot.toPublicProfile() = Profile(
    id = id, fullName = getString("displayName"), avatarUrl = getString("avatarUrl"), role = getString("role") ?: "customer",
)

@Suppress("UNCHECKED_CAST")
fun profileFromFunction(m: Map<String, Any?>) = Profile(
    id = m["id"] as String, email = m["email"] as? String, fullName = m["full_name"] as? String, avatarUrl = m["avatar_url"] as? String,
    role = m["role"] as? String ?: "customer", isActive = m["is_active"] as? Boolean ?: true,
    canManageProducts = m["can_manage_products"] as? Boolean ?: false, canUpdateOrders = m["can_update_orders"] as? Boolean ?: true,
    canManageP2p = m["can_manage_p2p"] as? Boolean ?: false, canViewStats = m["can_view_stats"] as? Boolean ?: false,
    createdAt = m["created_at"] as? String,
)

fun DocumentSnapshot.toCategory() = Category(
    id = id, slug = getString("slug") ?: id, name = getString("name") ?: "", description = getString("description"),
    colorKey = getString("colorKey"), iconKey = getString("iconKey") ?: id, sortOrder = int("sortOrder"), isActive = bool("active", true),
)

fun DocumentSnapshot.toSection() = Section(
    id = id, categoryId = getString("categoryId") ?: "", name = getString("name") ?: "", description = getString("description"),
    imageUrl = getString("imageUrl"), assignedStaffId = getString("assignedStaffId"), sortOrder = int("sortOrder"), isActive = bool("active", true),
)

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toProduct() = Product(
    id = id, categoryId = getString("categoryId") ?: "", sectionId = getString("sectionId"), name = getString("name") ?: "",
    description = getString("description"), priceUsd = num("priceUsd"), priceDzd = num("priceDzd"),
    stock = (get("stock") as? Number)?.toInt(), isAvailable = bool("available", true),
    images = (get("images") as? List<String>).orEmpty(), createdAt = ts("createdAt"),
)

fun DocumentSnapshot.toBanner() = Banner(
    id = id, title = getString("title") ?: "", subtitle = getString("subtitle"), imageUrl = getString("imageUrl"),
    categorySlug = getString("categorySlug"), sortOrder = int("sortOrder"), isActive = bool("active", true),
)

fun DocumentSnapshot.toP2p() = P2pAsset(
    id = id, code = getString("code") ?: "", name = getString("name") ?: "", network = getString("network"),
    buyPriceDzd = num("buyPriceDzd"), sellPriceDzd = num("sellPriceDzd"), minAmount = numOrNull("minAmount"),
    maxAmount = numOrNull("maxAmount"), isActive = bool("active", true), sortOrder = int("sortOrder"), updatedAt = ts("updatedAt"),
)

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toOrder(): Order {
    val items = (get("items") as? List<Map<String, Any?>>).orEmpty().mapIndexed { i, m ->
        OrderItem(
            id = "$id-$i", orderId = id, productId = m["productId"] as? String, productName = m["productName"] as? String ?: "",
            imageUrl = m["imageUrl"] as? String, quantity = (m["quantity"] as? Number)?.toInt() ?: 1,
            unitPriceUsd = (m["unitPriceUsd"] as? Number)?.toDouble() ?: 0.0, unitPriceDzd = (m["unitPriceDzd"] as? Number)?.toDouble() ?: 0.0,
        )
    }
    return Order(
        id = id, reference = getString("reference") ?: id, customerId = getString("customerId") ?: "", staffId = getString("staffId"),
        sectionId = getString("sectionId"), status = getString("status") ?: "open", totalUsd = num("totalUsd"), totalDzd = num("totalDzd"),
        note = getString("note"), createdAt = ts("createdAt") ?: "", updatedAt = ts("updatedAt"), paidAt = ts("paidAt"),
        deliveredAt = ts("deliveredAt"), closedAt = ts("closedAt"), items = items,
        payment = (get("payment") as? Map<String, Any?>)?.let { p ->
            fun d(k: String) = (p[k] as? Number)?.toDouble()
            PaymentInfo(status = p["status"] as? String ?: "waiting", invoiceUrl = p["invoiceUrl"] as? String, amountUsd = d("amountUsd") ?: 0.0,
                paidUsd = d("paidUsd"), feeUsd = d("feeUsd"), commissionUsd = d("commissionUsd"), commissionPct = d("commissionPct"), sellerUsd = d("sellerUsd"))
        },
    )
}

fun DocumentSnapshot.toConversation() = Conversation(
    id = id, customerId = getString("customerId") ?: "", staffId = getString("staffId"), orderId = getString("orderId"),
    productId = getString("productId"), sectionId = getString("sectionId"), subject = getString("subject"),
    lastMessage = getString("lastMessage"), lastMessageAt = ts("lastMessageAt"), createdAt = ts("createdAt"),
    lastSenderId = getString("lastSenderId"),
)

fun DocumentSnapshot.toMessage(conversationId: String): Message {
    val a = map("attachment")
    return Message(
        id = id, conversationId = conversationId, senderId = getString("senderId") ?: "", body = getString("body"),
        attachmentPath = a["path"] as? String, attachmentType = a["type"] as? String, attachmentName = a["name"] as? String,
        createdAt = ts("createdAt") ?: java.time.OffsetDateTime.now().toString(),
    )
}

fun DocumentSnapshot.toNotification(userId: String) = AppNotification(
    id = id, userId = userId, title = getString("title") ?: "", body = getString("body"),
    conversationId = getString("conversationId"), orderId = getString("orderId"), isRead = bool("read", false),
    createdAt = ts("createdAt") ?: "",
)
