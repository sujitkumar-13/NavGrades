# ProGuard Rules for NavGrades Android App

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Preserve Kotlinx Serialization classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

-keep,allowobfuscation,allowshrinking class * {
    @kotlinx.serialization.Serializable class *;
}

-keepclassmembers class * {
    *** Companion;
}

# Preserve Supabase and Room Data Models
-keep class com.example.data.remote.model.** { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.auth.** { *; }

# Google Credential Manager & Play Services Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
