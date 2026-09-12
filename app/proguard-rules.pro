# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

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


# ---------------------------------------------------------
# OkHttp optional TLS providers
# ---------------------------------------------------------

-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider

-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier

-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# ---------------------------------------------------------
# Retrofit / R8 Full Mode
# ---------------------------------------------------------

# Retrofit이 Call<ResponseBody>, Response<T> 등의
# Generic 타입 정보를 읽을 수 있도록 유지
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit의 @GET, @POST, @Multipart, @Part 등의 Annotation 유지
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Annotation 기본값 유지
-keepattributes AnnotationDefault

# Retrofit API 메서드 정보 유지
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# R8 Full Mode에서 Retrofit Service Interface 제거 방지
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# 상속된 Retrofit Interface 보호
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# Kotlin suspend 함수 Generic 정보 보호
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation

# Retrofit 메서드 반환 타입의 Generic Signature 보호
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Retrofit Response Generic 정보 보호
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

# Retrofit 선택적 클래스 경고 제거
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---------------------------------------------------------
# Gson DTO
# ---------------------------------------------------------

-keep class com.sylovestp.firebasetest.testspringrestapp.dto.** {
    *;
}