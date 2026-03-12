# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ===== AGGRESSIVE SPEED OPTIMIZATIONS =====
# Maximum optimization passes
-optimizationpasses 7

# Allow aggressive modifications for speed
-allowaccessmodification
-repackageclasses ''
-mergeinterfacesaggressively
-overloadaggressively

# Enable ALL speed-focused optimizations
# method/inlining/* - inline short methods for speed
# code/removal/* - remove dead code
# code/allocation/* - optimize object allocations
# class/unboxing/* - unbox wrapper types
# method/propagation/* - propagate constants
-optimizations method/inlining/*,code/removal/*,code/allocation/*,class/unboxing/*,method/propagation/*,field/propagation/*,class/marking/final

# Disable only problematic optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast

# Keep important Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Keep ZXing barcode scanner classes
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile