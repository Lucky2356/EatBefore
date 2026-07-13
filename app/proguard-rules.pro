# EatBefore ProGuard/R8 rules

# Keep Kotlinx Serialization metadata for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generated code is kept by the Room consumer rules; nothing extra needed.

# Do not strip enum names used for persistence/analytics mapping.
-keepclassmembers enum com.eatbefore.** { *; }
