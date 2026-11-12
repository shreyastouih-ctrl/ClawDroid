# Shizuku rules
-keep class dev.rikka.shizuku.** { *; }

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.openclaw.nativeapp.ai.** { *; }
-keep class com.openclaw.nativeapp.telegram.** { *; }
