import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// google-services.json is NOT committed. It is supplied locally or by CI (GitHub Secret).
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("MED-K: app/google-services.json is missing — Firebase will show a configuration screen at runtime.")
}

// Public client configuration (anon key is designed to be public; NEVER put service_role here).
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

        buildConfigField("String", "SUPABASE_URL", "\"${cfg("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${cfg("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("Boolean", "HAS_GOOGLE_SERVICES", hasGoogleServices.toString())
        resourceConfigurations += listOf("ar", "en")
    }

    signingConfigs {
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
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
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

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.coil.compose)
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
