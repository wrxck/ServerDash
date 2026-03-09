# Keep JavascriptInterface methods
-keepclassmembers class com.serverdash.ide.MonacoBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep MonacoBridge class name (referenced from JS as "AndroidBridge")
-keepnames class com.serverdash.ide.MonacoBridge
