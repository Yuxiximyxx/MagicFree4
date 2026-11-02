# Keep Xposed module entry point and all hook classes
-keep class io.github.yuxiximyxx.magicfree4.hook.** { *; }
-keep class io.github.yuxiximyxx.magicfree4.pods.** { *; }
-keep class io.github.yuxiximyxx.magicfree4.config.** { *; }

# Keep launcher icon resources (referenced only from AndroidManifest, not from Kotlin)
-keep class **.R$* { public static <fields>; }
-keepclassmembers class **.R$mipmap { *; }
