-dontwarn "org.bouncycastle.jsse.BCSSLParameters"
-dontwarn "org.bouncycastle.jsse.BCSSLSocket"
-dontwarn "org.bouncycastle.jsse.provider.BouncyCastleJsseProvider"
-dontwarn "org.conscrypt.Conscrypt$Version"
-dontwarn "org.conscrypt.Conscrypt"
-dontwarn "org.openjsse.javax.net.ssl.SSLParameters"
-dontwarn "org.openjsse.javax.net.ssl.SSLSocket"
-dontwarn "org.openjsse.net.ssl.OpenJSSE"
-dontwarn com.oracle.svm.core.annotate.Delete
-dontwarn com.oracle.svm.core.annotate.Substitute
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# Reflection usage in DankChatInputLayout
-keep class com.google.android.material.textfield.TextInputLayout { *** endLayout; }
-keep class com.google.android.material.textfield.EndCompoundLayout { *** endIconView; }
