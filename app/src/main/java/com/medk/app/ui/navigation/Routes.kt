package com.medk.app.ui.navigation

import android.net.Uri
import androidx.navigation.NavHostController

object Routes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val CART = "cart"
    const val CONVERSATIONS = "conversations"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val ORDERS = "orders"
    const val FAVORITES = "favorites"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val CATEGORY = "category/{slug}"
    const val SECTION = "section/{id}"
    const val PRODUCT = "product/{id}"
    const val CHAT = "chat/{id}"
    const val ORDER = "order/{id}"
    const val MEDIA = "media?url={url}&type={type}"

    const val WORKSPACE = "workspace"
    const val ADMIN_ORDERS = "admin/orders?status={status}"
    const val ADMIN_PRODUCTS = "admin/products"
    const val PRODUCT_EDIT = "admin/product?id={id}"
    const val ADMIN_CATEGORIES = "admin/categories"
    const val ADMIN_SECTIONS = "admin/sections"
    const val ADMIN_STAFF = "admin/staff"
    const val STAFF_DETAIL = "admin/staff/{id}"
    const val ADMIN_BANNERS = "admin/banners"
    const val ADMIN_P2P = "admin/p2p"
    const val ADMIN_MORE = "admin/more"
    const val ADMIN_CUSTOMERS = "admin/customers"
    const val WALLET = "wallet"
    const val ADMIN_PAYMENTS = "admin/payments"
    const val CUSTOMER_DETAIL = "admin/customer/{id}"
    fun customerDetail(id: String) = "admin/customer/$id"

    val tabs = listOf(HOME, CATEGORIES, CART, CONVERSATIONS, PROFILE)

    fun category(slug: String) = "category/$slug"
    fun section(id: String) = "section/$id"
    fun product(id: String) = "product/$id"
    fun chat(id: String) = "chat/$id"
    fun order(id: String) = "order/$id"
    fun media(url: String, type: String) = "media?url=${Uri.encode(url)}&type=$type"
    fun adminOrders(status: String? = null) = "admin/orders" + (status?.let { "?status=$it" } ?: "")
    fun productEdit(id: String? = null) = "admin/product" + (id?.let { "?id=$it" } ?: "")
    fun staffDetail(id: String) = "admin/staff/$id"
}

/** Navigates to a bottom-bar tab, keeping a single copy of each tab on the stack. */
fun NavHostController.toTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
