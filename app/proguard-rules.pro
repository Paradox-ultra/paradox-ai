# Paradox AI - ProGuard rules (release builds)
# OkHttp / OkIO
-dontwarn okhttp3.**
-dontwarn okio.**
# org.json is provided by the Android framework
-dontwarn org.json.**
# Keep Kotlin metadata for reflection-based tooling
-keepclassmetadata class * { <fields>; <methods>; }
