# LaundrySlot R8 / ProGuard Configuration

# Preserve Firestore domain model classes, no-arg constructors, fields, and enums
# used by Firebase CustomClassMapper reflection.
-keepclassmembers class com.example.aasha.domain.model.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keepnames class com.example.aasha.domain.model.**
-keepclassmembers enum com.example.aasha.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip verbose and debug logs from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
