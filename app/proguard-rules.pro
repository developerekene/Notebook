# Preserve line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.devekene.notebook.domain.model.**$$serializer { *; }
-keep,includedescriptorclasses class com.devekene.notebook.core.crypto.**$$serializer { *; }
-keep,includedescriptorclasses class com.devekene.notebook.core.export.**$$serializer { *; }
-keep,includedescriptorclasses class com.devekene.notebook.ui.navigation.**$$serializer { *; }

# Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class com.devekene.notebook.data.local.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }
