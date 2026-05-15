# Keep all MediaPipe / GenAI classes and JNI entry points (required for release builds).
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn com.google.mediapipe.**

# 1. Keep the MediaPipe GenAI tasks
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }

# 2. Keep the JNI bridge classes (important for M5/ARM64)
-keep class com.google.mediapipe.tasks.core.** { *; }

# 3. Prevent obfuscation of the native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Prevent R8 from deleting the MediaPipe AI Engine
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}
# Ignore missing Java processing classes (Safe for Android)
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**

# Keep AutoValue processors from causing R8 issues
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**

# General safety for MediaPipe/GenAI
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}