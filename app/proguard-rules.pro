# Add project specific ProGuard rules here.
-keep class com.voiceassistant.core.llm.** { *; }
-keep class com.voiceassistant.core.voice.** { *; }
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlinx.serialization.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
