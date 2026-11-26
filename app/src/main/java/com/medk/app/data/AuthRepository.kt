package com.medk.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.medk.app.BuildConfig
import com.medk.app.core.AppContainer
import com.medk.app.core.friendly
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data object NotConfigured : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val profile: Profile) : AuthState
    data class Failed(val message: String) : AuthState
    /** Signed up with e-mail + password: the account is unusable until the e-mail link is opened. */
    data class VerifyEmail(val email: String) : AuthState
}

class GoogleSignInCancelled : Exception("cancelled")

internal val DEFAULT_PERMS = mapOf("manageProducts" to false, "updateOrders" to true, "manageP2p" to false, "viewStats" to false)
internal val OWNER_PERMS = mapOf("manageProducts" to true, "updateOrders" to true, "manageP2p" to true, "viewStats" to true)

/** Firebase Authentication (email/password + Google) bridged to the MED-K backend profile. */
open class AuthRepository(private val c: AppContainer) {

    protected val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    open val state: StateFlow<AuthState> = _state.asStateFlow()

    open val currentUid: String? get() = c.firebaseAuth?.currentUser?.uid
    open val profile: Profile? get() = (state.value as? AuthState.SignedIn)?.profile

    init {
        if (!c.isConfigured) _state.value = AuthState.NotConfigured
        else if (c.firebaseAuth?.currentUser == null) _state.value = AuthState.SignedOut
        else c.appScope.launch { restore() }
    }

    /** Re-validates the stored Firebase session with the server at app start. */
    open suspend fun restore() {
        _state.value = AuthState.Loading
        needsVerification()?.let { _state.value = it; return }
        _state.value = runCatching { AuthState.SignedIn(bootstrap(null)) }.getOrElse { e ->
            if (e.message?.contains("account_disabled") == true) {
                c.firebaseAuth?.signOut(); AuthState.SignedOut
            } else AuthState.Failed(e.friendly())
        }
    }

    /**
     * Free plan (no server): creates/loads users/{uid}. The security rules decide what is allowed —
     * a profile can only be created as "customer", or as "owner" for a verified owner e-mail.
     */
    private suspend fun bootstrap(fullName: String?): Profile {
        val user = c.firebaseAuth?.currentUser ?: error("not signed in")
        val db = c.firestore
        val ref = db.collection("users").document(user.uid)
        var snap = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        val verified = user.isEmailVerified
        if (!snap.exists()) {
            fun profile(role: String, perms: Map<String, Boolean>) = mapOf(
                "email" to user.email, "fullName" to (fullName ?: user.displayName)?.take(80), "avatarUrl" to user.photoUrl?.toString(),
                "role" to role, "active" to true, "perms" to perms, "sections" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
            val ownerTry = verified && runCatching { ref.set(profile("owner", OWNER_PERMS)).await() }.isSuccess
            val invited = !ownerTry && acceptInvite(null)
            if (!ownerTry && !invited) ref.set(profile("customer", DEFAULT_PERMS)).await()
            ownerProbeDone(user.uid)
            snap = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        } else if (snap.getString("role") == "customer" && verified && acceptInvite(snap)) {
            snap = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        } else if (snap.getString("role") == "customer" && verified && !isOwnerProbeDone(user.uid)) {
            // owner who signed up with a password: promoted once the e-mail is verified (rules check the list)
            runCatching { ref.update(mapOf("role" to "owner", "perms" to OWNER_PERMS, "updatedAt" to FieldValue.serverTimestamp())).await() }
            ownerProbeDone(user.uid)
            snap = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        }
        val p = snap.toProfile()
        if (!p.isActive) error("account_disabled")
        runCatching {
            db.collection("publicProfiles").document(p.id)
                .set(mapOf("displayName" to p.fullName, "avatarUrl" to p.avatarUrl, "role" to p.role)).await()
        }
        if (p.roleEnum == Role.OWNER) runCatching { seedCatalogIfEmpty() }
        c.notifications.start(p.id)
        c.push.register(p.id)
        return p
    }

    /**
     * The owner invited this (verified) e-mail as a seller: become a seller with exactly the invited permissions and
     * sections, consume the invitation and take the still-unassigned invited sections — one atomic batch, checked
     * by the security rules. Returns false when there is no usable invitation.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun acceptInvite(existing: com.google.firebase.firestore.DocumentSnapshot?): Boolean {
        val user = c.firebaseAuth?.currentUser ?: return false
        if (!user.isEmailVerified) return false
        val email = user.email?.lowercase() ?: return false
        val db = c.firestore
        val inv = runCatching { db.collection("invites").document(email).get(com.google.firebase.firestore.Source.SERVER).await() }
            .getOrNull() ?: return false
        if (!inv.exists() || inv.getString("acceptedBy") != null) return false
        val perms = inv.get("perms") as? Map<String, Any?> ?: return false
        val sections = (inv.get("sections") as? List<String>).orEmpty()
        val ref = db.collection("users").document(user.uid)
        return runCatching {
            val batch = db.batch()
            if (existing == null) batch.set(ref, mapOf(
                "email" to user.email, "fullName" to (inv.getString("name") ?: user.displayName)?.take(80),
                "avatarUrl" to user.photoUrl?.toString(), "role" to "staff", "active" to true, "perms" to perms, "sections" to sections,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            else batch.update(ref, mapOf("role" to "staff", "perms" to perms, "sections" to sections, "updatedAt" to FieldValue.serverTimestamp()))
            batch.update(inv.reference, mapOf("acceptedBy" to user.uid, "acceptedAt" to FieldValue.serverTimestamp()))
            for (sid in sections) {
                val sec = db.collection("sections").document(sid).get().await()
                if (sec.exists() && sec.getString("assignedStaffId") == null) batch.update(sec.reference, "assignedStaffId", user.uid)
            }
            batch.commit().await()
        }.onFailure { android.util.Log.w("MedkAuth", "invite acceptance failed", it) }.isSuccess
    }

    private val probePrefs by lazy { c.context.getSharedPreferences("medk_owner_probe", android.content.Context.MODE_PRIVATE) }
    private fun isOwnerProbeDone(uid: String) = probePrefs.getBoolean(uid, false)
    private fun ownerProbeDone(uid: String) { if (c.firebaseAuth?.currentUser?.isEmailVerified == true) probePrefs.edit().putBoolean(uid, true).apply() }

    /** First owner sign-in: the five fixed categories and their sections (structure only, no products). */
    private suspend fun seedCatalogIfEmpty() {
        val db = c.firestore
        if (!db.collection("categories").limit(1).get().await().isEmpty) return
        val cats = listOf(
            Triple("gaming", "شحن الألعاب", "جواهر، شدات، عملات داخل الألعاب") to "orange",
            Triple("subscriptions", "اشتراكات التطبيقات", "Netflix - YouTube - Spotify وأكثر") to "blue",
            Triple("accounts", "شراء الحسابات", "حسابات الألعاب والتطبيقات") to "purple",
            Triple("p2p", "شراء وبيع العملات الرقمية P2P", "USDT والعملات الرقمية المدعومة") to "green",
            Triple("other", "خدمات أخرى", "بطاقات الهدايا، تراخيص البرامج، VPN والخدمات الرقمية المتنوعة") to "cyan",
        )
        val sections = mapOf(
            "gaming" to listOf("Free Fire", "PUBG Mobile", "Call of Duty Mobile", "Roblox", "ألعاب أخرى"),
            "subscriptions" to listOf("Netflix", "YouTube Premium", "Spotify", "تطبيقات أخرى"),
            "accounts" to listOf("حسابات الألعاب", "حسابات التطبيقات"),
            "p2p" to listOf("العملات الرقمية"),
            "other" to listOf("بطاقات الهدايا", "Google Play", "Apple Gift Card", "تراخيص البرامج", "اشتراكات VPN", "منتجات رقمية أخرى"),
        )
        val batch = db.batch()
        cats.forEachIndexed { i, (t, color) ->
            batch.set(db.collection("categories").document(t.first), mapOf("slug" to t.first, "name" to t.second, "description" to t.third,
                "colorKey" to color, "iconKey" to t.first, "sortOrder" to i + 1, "active" to true))
        }
        batch.commit().await()   // categories must exist before sections reference them
        val b2 = db.batch()
        sections.forEach { (cat, names) -> names.forEachIndexed { i, n ->
            b2.set(db.collection("sections").document(), mapOf("categoryId" to cat, "name" to n, "description" to null, "imageUrl" to null,
                "assignedStaffId" to null, "sortOrder" to i + 1, "active" to true, "createdAt" to FieldValue.serverTimestamp()))
        } }
        b2.commit().await()
        c.catalog.invalidate()
    }

    /** Ordering requires a verified e-mail (anti-spam). Re-checks with the server and refreshes the token. */
    open suspend fun ensureEmailVerified() {
        val user = c.firebaseAuth?.currentUser ?: error("not signed in")
        if (user.isEmailVerified) return
        user.reload().await()
        val u = c.firebaseAuth?.currentUser ?: error("not signed in")
        if (u.isEmailVerified) { u.getIdToken(true).await(); return }
        runCatching { u.sendEmailVerification().await() }
        error("email_not_verified")
    }

    open suspend fun signIn(email: String, password: String) {
        val auth = c.firebaseAuth ?: error("Firebase not configured")
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        finishSignIn(null)
    }

    open suspend fun register(name: String, email: String, password: String) {
        val auth = c.firebaseAuth ?: error("Firebase not configured")
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        result.user?.updateProfile(userProfileChangeRequest { displayName = name.trim() })?.await()
        c.firebaseAuth?.setLanguageCode("ar")
        runCatching { result.user?.sendEmailVerification()?.await() }
        finishSignIn(name.trim())   // → VerifyEmail screen (locked until the link is opened)
    }

    open suspend fun sendPasswordReset(email: String) {
        val auth = c.firebaseAuth ?: error("Firebase not configured")
        auth.setLanguageCode("ar")
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Google Sign-In via Credential Manager → Firebase credential. */
    open suspend fun signInWithGoogle(activityContext: Context) {
        val auth = c.firebaseAuth ?: error("Firebase not configured")
        val clientId = webClientId(activityContext)
        require(clientId.isNotBlank()) { "لم يتم إعداد Google Web Client ID" }
        val cm = CredentialManager.create(activityContext)
        val response = try {
            val req = GetCredentialRequest.Builder().addCredentialOption(
                GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId)
                    .setAutoSelectEnabled(false).build()
            ).build()
            cm.getCredential(activityContext, req)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        } catch (e: NoCredentialException) {
            // No Google account on device for the bottom sheet → fall back to the full "Sign in with Google" flow.
            try {
                cm.getCredential(activityContext, GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build())
            } catch (e2: GetCredentialCancellationException) { throw GoogleSignInCancelled() }
        }
        val cred = response.credential
        if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            error("unexpected credential")
        val google = GoogleIdTokenCredential.createFrom(cred.data)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        finishSignIn(google.displayName)
    }

    /** Password accounts must confirm their e-mail first (Google accounts are already verified). */
    private suspend fun needsVerification(): AuthState.VerifyEmail? {
        val user = c.firebaseAuth?.currentUser ?: return null
        if (user.isEmailVerified) return null
        runCatching { user.reload().await() }
        val u = c.firebaseAuth?.currentUser ?: return null
        if (u.isEmailVerified || u.providerData.none { it.providerId == "password" }) return null
        return AuthState.VerifyEmail(u.email ?: "")
    }

    private var pendingName: String? = null

    /** "I opened the link": re-reads the account; continues into the app once verified. Returns false if not yet. */
    open suspend fun checkEmailVerified(): Boolean {
        val user = c.firebaseAuth?.currentUser ?: return false
        user.reload().await()
        val u = c.firebaseAuth?.currentUser ?: return false
        if (!u.isEmailVerified) return false
        u.getIdToken(true).await()   // the security rules read email_verified from a fresh token
        finishSignIn(pendingName)
        return true
    }

    open suspend fun resendVerification() {
        c.firebaseAuth?.setLanguageCode("ar")   // Arabic confirmation e-mail
        c.firebaseAuth?.currentUser?.sendEmailVerification()?.await() ?: error("not signed in")
    }

    private suspend fun finishSignIn(name: String?) {
        needsVerification()?.let { pendingName = name; _state.value = it; return }
        try {
            _state.value = AuthState.SignedIn(bootstrap(name))
        } catch (e: Exception) {
            c.firebaseAuth?.signOut()
            _state.value = AuthState.SignedOut
            throw e
        }
    }

    open suspend fun refreshProfile() {
        val uid = currentUid ?: return
        val p = c.firestore.collection("users").document(uid).get().await().toProfile()
        _state.value = AuthState.SignedIn(p)
    }

    open suspend fun updateName(name: String) {
        val uid = currentUid ?: return
        c.firebaseAuth?.currentUser?.updateProfile(userProfileChangeRequest { displayName = name })?.await()
        c.firestore.collection("users").document(uid)
            .update(mapOf("fullName" to name, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
        refreshProfile()
    }

    open val isPasswordAccount: Boolean
        get() = c.firebaseAuth?.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    open suspend fun changePassword(current: String, newPassword: String) {
        val user = c.firebaseAuth?.currentUser ?: error("not signed in")
        user.reauthenticate(EmailAuthProvider.getCredential(user.email ?: "", current)).await()
        user.updatePassword(newPassword).await()
    }

    open suspend fun signOut(context: Context) {
        c.notifications.stop()
        c.push.unregister()
        c.firebaseAuth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
        c.cart.clear()
        _state.value = AuthState.SignedOut
    }

    private fun webClientId(ctx: Context): String {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else BuildConfig.GOOGLE_WEB_CLIENT_ID
    }
}
