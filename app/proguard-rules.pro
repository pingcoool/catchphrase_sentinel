# Vosk / JNA 使用 JNI，保留相关类与 native 方法
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
