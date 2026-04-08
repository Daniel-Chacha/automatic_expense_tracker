# Room
-keep class com.personal.expensetracker.data.local.entity.** { *; }

# Supabase + Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.personal.expensetracker.**$$serializer { *; }
-keepclassmembers class com.personal.expensetracker.** { *** Companion; }
-keepclasseswithmembers class com.personal.expensetracker.** { kotlinx.serialization.KSerializer serializer(...); }
