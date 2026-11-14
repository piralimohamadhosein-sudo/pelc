# JNI bridge — exact symbol resolution
-keep class ir.pelc.core.shaping.HarfBuzzJni { *; }

# kotlinx.serialization for the document model
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class ir.pelc.core.model.**$$serializer { *; }
-keepclassmembers class ir.pelc.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class ir.pelc.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
