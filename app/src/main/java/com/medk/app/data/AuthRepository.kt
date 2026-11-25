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
}

class GoogleSignInCancelled : Exception("cancelled")

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
        _state.value = runCatching { AuthState.SignedIn(bootstrap(null)) }.getOrElse { e ->
            if (e.message?.contains("account_disabled") == true || e.message?.contains("unauthenticated", true) == true) {
                c.firebaseAuth?.signOut(); AuthState.SignedOut
            } else AuthState.Failed(e.friendly())
        }
    }

    /** Server-side bootstrap (Cloud Function): creates the profile and syncs the role claim. */
    private suspend fun bootstrap(fullName: String?): Profile {
        val res = c.cloud.call("bootstrap", if (fullName != null) mapOf("full_name" to fullName) else emptyMap())
        if (res["claims_updated"] == true) c.firebaseAuth?.currentUser?.getIdToken(true)?.await()
        @Suppress("UNCHECKED_CAST")
        val p = profileFromFunction(res["profile"] as Map<String, Any?>)
        c.notifications.start(p.id)
        return p
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
        runCatching { result.user?.sendEmailVerification()?.await() }
        finishSignIn(name.trim())
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

    private suspend fun finishSignIn(name: String?) {
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
