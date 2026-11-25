package com.medk.app.core

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug builds: App Check debug provider (register the logged debug token in the Firebase console). */
object AppCheckSetup {
    fun install() = FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
