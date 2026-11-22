# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.medk.app.**$$serializer { *; }
-keepclassmembers class com.medk.app.** { *** Companion; }
-keepclasseswithmembers class com.medk.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class com.medk.app.** { *; }

# Ktor / Supabase
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
