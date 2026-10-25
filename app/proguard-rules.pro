# Proguard & R8 Optimization Rules for Syntaxa Khata

# 1. Android & Kotlin Core
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Room Database & Entities
-keep class * extends androidx.room.RoomDatabase
-keep class com.syntaxa.khata.data.local.entity.** { *; }
-keep class com.syntaxa.khata.domain.model.** { *; }
-keep class com.syntaxa.khata.data.local.KhataDatabase** { *; }
-keep interface com.syntaxa.khata.data.local.dao.** { *; }
-keep class * implements androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# 3. Enums (used in entities, transaction types, etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 4. KotlinX Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# 5. AndroidX Biometric & DataStore
-keep class androidx.biometric.** { *; }
-keep class androidx.datastore.** { *; }

# 6. Compose Runtime
-keepclassmembers class * extends androidx.compose.runtime.State { *; }

# 7. Syntaxa Khata Application, ViewModels, Feature Models, & Security
-keep class com.syntaxa.khata.** { *; }
-keepclassmembers class com.syntaxa.khata.** { *; }

# 8. Activity Result Contracts & Activity Compose
-keep class androidx.activity.result.** { *; }
-keepclassmembers class androidx.activity.result.** { *; }
-keep class androidx.activity.compose.** { *; }
-keepclassmembers class androidx.activity.compose.** { *; }
