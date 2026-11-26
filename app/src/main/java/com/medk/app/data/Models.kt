package com.medk.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Role { CUSTOMER, STAFF, OWNER;
    companion object { fun of(v: String?) = when (v) { "owner" -> OWNER; "staff" -> STAFF; else -> CUSTOMER } }
}

@Serializable
data class Profile(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "customer",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("can_manage_products") val canManageProducts: Boolean = false,
    @SerialName("can_update_orders") val canUpdateOrders: Boolean = true,
    @SerialName("can_manage_p2p") val canManageP2p: Boolean = false,
    @SerialName("can_view_stats") val canViewStats: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val permissions get() = StaffPermissions(canManageProducts, canUpdateOrders, canManageP2p, canViewStats)
    val roleEnum get() = Role.of(role)
    val displayName get() = fullName?.takeIf { it.isNotBlank() } ?: email ?: "مستخدم"
}

@Serializable
data class Category(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    @SerialName("color_key") val colorKey: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class Section(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("assigned_staff_id") val assignedStaffId: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class Product(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("section_id") val sectionId: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("price_usd") val priceUsd: Double = 0.0,
    @SerialName("price_dzd") val priceDzd: Double = 0.0,
    val stock: Int? = null,
    @SerialName("is_available") val isAvailable: Boolean = true,
    val images: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
) {
    val inStock get() = isAvailable && (stock == null || stock > 0)
    val cover get() = images.firstOrNull()
}

/** Write payload for products (no id/created_at). */
@Serializable
data class ProductInput(
    @SerialName("category_id") val categoryId: String,
    @SerialName("section_id") val sectionId: String?,
    val name: String,
    val description: String?,
    @SerialName("price_usd") val priceUsd: Double,
    @SerialName("price_dzd") val priceDzd: Double,
    val stock: Int?,
    @SerialName("is_available") val isAvailable: Boolean,
    val images: List<String>,
)

@Serializable
data class SectionInput(
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String?,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("assigned_staff_id") val assignedStaffId: String?,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class Banner(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("category_slug") val categorySlug: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class BannerInput(
    val title: String,
    val subtitle: String?,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("category_slug") val categorySlug: String?,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class P2pAsset(
    val id: String,
    val code: String,
    val name: String,
    val network: String? = null,
    @SerialName("buy_price_dzd") val buyPriceDzd: Double = 0.0,
    @SerialName("sell_price_dzd") val sellPriceDzd: Double = 0.0,
    @SerialName("min_amount") val minAmount: Double? = null,
    @SerialName("max_amount") val maxAmount: Double? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class P2pAssetInput(
    val code: String,
    val name: String,
    val network: String?,
    @SerialName("buy_price_dzd") val buyPriceDzd: Double,
    @SerialName("sell_price_dzd") val sellPriceDzd: Double,
    @SerialName("min_amount") val minAmount: Double?,
    @SerialName("max_amount") val maxAmount: Double?,
    @SerialName("is_active") val isActive: Boolean,
)

enum class OrderStatus(val key: String, val label: String) {
    OPEN("open", "مفتوح"), PAID("paid", "تم الدفع"), DELIVERED("delivered", "تم التسليم"), CLOSED("closed", "مغلق");
    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: OPEN }
}

@Serializable
data class OrderItem(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val quantity: Int,
    @SerialName("unit_price_usd") val unitPriceUsd: Double,
    @SerialName("unit_price_dzd") val unitPriceDzd: Double,
)

@Serializable
data class Order(
    val id: String,
    val reference: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("staff_id") val staffId: String? = null,
    @SerialName("section_id") val sectionId: String? = null,
    val status: String = "open",
    @SerialName("total_usd") val totalUsd: Double = 0.0,
    @SerialName("total_dzd") val totalDzd: Double = 0.0,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("order_items") val items: List<OrderItem> = emptyList(),
) {
    val statusEnum get() = OrderStatus.of(status)
}

@Serializable
data class Conversation(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("staff_id") val staffId: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("section_id") val sectionId: String? = null,
    val subject: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_sender_id") val lastSenderId: String? = null,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String? = null,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    @SerialName("attachment_type") val attachmentType: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MessageInput(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String?,
    @SerialName("attachment_path") val attachmentPath: String? = null,
    @SerialName("attachment_type") val attachmentType: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
)

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val body: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Favorite(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
)

@Serializable
data class StaffSection(
    @SerialName("staff_id") val staffId: String,
    @SerialName("section_id") val sectionId: String,
)

@Serializable
data class CreatedOrder(
    @SerialName("order_id") val orderId: String,
    @SerialName("conversation_id") val conversationId: String,
    val reference: String,
)

@Serializable
data class DashboardStats(
    @SerialName("total_orders") val totalOrders: Int = 0,
    @SerialName("open_orders") val openOrders: Int = 0,
    @SerialName("paid_orders") val paidOrders: Int = 0,
    @SerialName("delivered_orders") val deliveredOrders: Int = 0,
    @SerialName("closed_orders") val closedOrders: Int = 0,
    @SerialName("revenue_usd") val revenueUsd: Double = 0.0,
    @SerialName("revenue_dzd") val revenueDzd: Double = 0.0,
    @SerialName("open_conversations") val openConversations: Int = 0,
    @SerialName("unassigned_orders") val unassignedOrders: Int = 0,
    val products: Int = 0,
    val staff: Int = 0,
    val customers: Int = 0,
)

/** Locally persisted cart line (prices are re-validated server-side at checkout). */
@Serializable
data class CartLine(
    val productId: String,
    val name: String,
    val image: String? = null,
    val priceUsd: Double,
    val priceDzd: Double,
    val quantity: Int,
    val sectionId: String? = null,
    val sectionName: String? = null,
)

/** Fine-grained permissions the owner grants to each seller. */
data class StaffPermissions(
    val manageProducts: Boolean = false,
    val updateOrders: Boolean = true,
    val manageP2p: Boolean = false,
    val viewStats: Boolean = false,
)
