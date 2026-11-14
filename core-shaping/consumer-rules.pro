# Keep the JNI bridge: its native symbols are resolved by exact class/member names.
-keep class ir.pelc.core.shaping.HarfBuzzJni { *; }
-keepclassmembers class ir.pelc.core.shaping.** { *; }
