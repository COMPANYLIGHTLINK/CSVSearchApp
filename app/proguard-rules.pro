# Add project specific ProGuard rules here.
-keep class com.csvapp.data.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
