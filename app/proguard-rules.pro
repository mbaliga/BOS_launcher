# Sphere Launcher — keep rules. Minification is disabled for now (M1); rules will be
# tightened as modules stabilise.

# Services / receivers referenced only from the manifest are kept by AAPT rules; keep our enums'
# names stable because prefs persist them by name (Enum.valueOf / entries lookups).
-keepclassmembers enum com.bos.sphere.core.data.** { *; }
-dontwarn org.json.**
