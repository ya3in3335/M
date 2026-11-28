import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// google-services.json is public client config (project medk-app-261126-7730); data is protected by Firestore rules.
// CI may override it with the GOOGLE_SERVICES_JSON secret.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("MED-K: app/google-services.json is missing — Firebase will show a configuration screen at runtime.")
}

// Public client configuration only. No server secret ever ships in the app.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String): String =
    (System.getenv(key) ?: secrets.getProperty(key) ?: project.findProperty(key)?.toString() ?: "").trim()

android {
    namespace = "com.medk.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medk.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${cfg("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("Boolean", "HAS_GOOGLE_SERVICES", hasGoogleServices.toString())
        resourceConfigurations += listOf("ar", "en")
        buildConfigField("Boolean", "DEMO", "false")
        // In-app updates are published as GitHub Releases of this repository.
        buildConfigField("String", "UPDATE_REPO", "\"ya3in3335/M\"")
    }

    // Two apps from one codebase:
    //   app  : store = customer/staff marketplace, admin = owner control panel ("MED-K Admin")
    //   mode : prod  = real Firebase backend,  demo = no backend, in-memory sample data
    flavorDimensions += listOf("app", "mode")
    productFlavors {
        create("store") { dimension = "app"; buildConfigField("Boolean", "ADMIN_APP", "false") }
        create("admin") {
            dimension = "app"
            applicationId = "com.medk.admin"
            buildConfigField("Boolean", "ADMIN_APP", "true")
        }
        create("prod") { dimension = "mode" }
        create("demo") {
            dimension = "mode"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("Boolean", "DEMO", "true")
        }
    }

    signingConfigs {
        // Shared debug/test signing key (not secret) so that every build — local or CI —
        // can be installed as an update over the previous one.
        getByName("debug") {
            storeFile = file("keys/medk-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val ks = System.getenv("MEDK_KEYSTORE_PATH") ?: secrets.getProperty("MEDK_KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = cfg("MEDK_KEYSTORE_PASSWORD")
                keyAlias = cfg("MEDK_KEY_ALIAS")
                keyPassword = cfg("MEDK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Production key when provided; otherwise the shared key, so release builds install over earlier
            // builds and keep the SHA-1 registered in Firebase (Google sign-in).
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ""
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { t ->
            // Optional: -ProbolectricDepsDir=/path to run screenshot tests offline
            (project.findProperty("robolectricDepsDir") as String?)?.let {
                t.systemProperty("robolectric.offline", "true")
                t.systemProperty("robolectric.dependency.dir", it)
            }
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// App name per variant.
androidComponents {
    onVariants { v ->
        val admin = v.productFlavors.any { it.second == "admin" }
        val demo = v.productFlavors.any { it.second == "demo" }
        val name = (if (admin) "MED-K Admin" else "MED-K") + (if (demo) " Demo" else "")
        v.resValues.put(v.makeResValueKey("string", "app_name"), com.android.build.api.variant.ResValue(name, null))
        // Release asset this variant updates itself from (e.g. MED-K-Admin-Demo.apk).
        val asset = name.replace(" ", "-") + ".apk"
        v.buildConfigFields.put("UPDATE_ASSET", com.android.build.api.variant.BuildConfigField("String", "\"$asset\"", null))
    }
}

// The demo flavor has its own applicationId and never talks to Firebase.
tasks.matching { it.name.contains("Demo") && it.name.contains("GoogleServices") }.configureEach { enabled = false }
// The admin app needs its own Android app (com.medk.admin) in the same Firebase project.
val googleJsonHasAdmin = hasGoogleServices && file("google-services.json").readText().contains("\"com.medk.admin\"")
if (hasGoogleServices && !googleJsonHasAdmin) {
    logger.warn("MED-K: google-services.json has no com.medk.admin client — MED-K Admin (prod) will show the configuration screen.")
    tasks.matching { it.name.contains("AdminProd") && it.name.contains("GoogleServices") }.configureEach { enabled = false }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.coil.network)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
