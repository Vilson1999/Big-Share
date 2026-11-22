# Repackage all obfuscated classes into the root package ('') rather than a subpackage ('a')
# to eliminate the 'a/' prefix from thousands of class descriptors in DEX string tables.
-repackageclasses ''

# Allow R8 to broaden visibility, merge interfaces aggressively, and overload method/field
# names across distinct descriptors for maximum DEX shrinking and obfuscation.
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# Strip original source file names if any dependency rule retains SourceFile attributes.
-renamesourcefileattribute SourceFile

# Strip all android.util.Log calls and short-circuit Log.isLoggable(...) to false so
# guarded debug logging blocks and their string allocations are dead-code eliminated.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int) return false;
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(int, java.lang.String, java.lang.String);
    public static java.lang.String getStackTraceString(java.lang.Throwable) return "";
}

# Strip Kotlin compiler-generated parameter/expression null-check string assertions.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
}

# Disable Kotlin runtime assertions.
-assumenosideeffects class kotlin._Assertions {
    public static final boolean ENABLED return false;
}

# Ensure Compose runtime tracing & debug inspector flags are dead-code eliminated across all AARs.
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    public static boolean isTraceInProgress() return false;
    public static void traceEventStart(int, int, int, java.lang.String);
    public static void traceEventStart(int, java.lang.String);
    public static void traceEventEnd();
}

-assumenosideeffects class androidx.compose.ui.platform.InspectableValueKt {
    public static boolean isDebugInspectorInfoEnabled() return false;
}

