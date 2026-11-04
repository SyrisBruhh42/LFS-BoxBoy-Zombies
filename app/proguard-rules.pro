# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleTypeAnnotations,RuntimeInvisibleTypeAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod

# Keep Kotlin Metadata & Serialization
-keep class kotlin.Metadata { *; }

# Keep Firestore & Local Save / Network Data Models
-keep class com.example.data.** { *; }
-keep class com.example.game.** { *; }
-keep class com.example.network.** { *; }

# Keep Enums for Firestore & Wire Deserialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Google Auth / Drive OAuth Reflection Keep Rules
-keep class com.google.android.gms.auth.GoogleAuthUtil { *; }
-dontwarn com.google.android.gms.auth.**
-dontwarn com.google.firebase.appcheck.debug.**

