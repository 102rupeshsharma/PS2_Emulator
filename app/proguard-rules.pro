# Settings are stored as JSON via kotlinx-serialization. The library ships its own R8 rules;
# these keep the persisted model explicitly so a shrinker change can never silently reset
# users' profiles to defaults (the codec falls back to defaults on any decode error).
-keep class com.ps2gamepad.model.** { *; }
-keepclassmembers class com.ps2gamepad.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses, Signature
