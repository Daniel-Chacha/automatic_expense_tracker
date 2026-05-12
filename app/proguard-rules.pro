# Room
-keep class com.personal.financetracker.data.local.entity.** { *; }

# PostgreSQL JDBC driver (Neon)
-keep class org.postgresql.** { *; }
-keep class org.postgresql.Driver
-dontwarn org.postgresql.**
-dontwarn javax.naming.**
-dontwarn javax.security.sasl.**
-dontwarn org.slf4j.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.personal.financetracker.**$$serializer { *; }
-keepclassmembers class com.personal.financetracker.** { *** Companion; }
-keepclasseswithmembers class com.personal.financetracker.** { kotlinx.serialization.KSerializer serializer(...); }
