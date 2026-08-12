# Deliberately near-empty.
#
# This app exists to prove that crates/tauri/mobile/android/consumer-rules.pro,
# applied transitively from the :tauri-android dependency, is by itself
# sufficient to keep reflective Tauri plugin discovery working under R8
# minification (see README.md). It must NOT add its own -keep rules for
# DiscoveryTestPlugin, its annotations, or its callback methods -- if this file
# needed one to make ReflectivePluginDiscoveryTest pass, that would mean the
# shipped consumer-rules.pro is not actually sufficient for a real consuming
# app, which is exactly the class of bug this module is here to catch.
-keepattributes SourceFile,LineNumberTable

# Unrelated to the thing under test: androidx.test's tracing code references
# a compile-only errorprone annotation that isn't on the runtime classpath.
-dontwarn com.google.errorprone.annotations.**

# Unrelated to the thing under test: AndroidJUnitRunner.onCreate() calls into
# androidx.tracing.Trace directly, but nothing else in this app references
# it, so R8 strips it as unused. Without this the instrumentation process
# crashes with NoClassDefFoundError before the test runner can even attach,
# which `am instrument` reports as a hang rather than a clean failure.
-keep class androidx.tracing.Trace { *; }

# Unrelated to the thing under test: AndroidJUnitRunner.onStart() ->
# androidx.test.platform.io.FileTestStorage's <init> references a
# Kotlin-compiled lambda, whose generated class extends kotlin.jvm.internal
# base classes (Lambda and the FunctionN interfaces it implements). Nothing
# else in this app is Kotlin-compiled androidx.test code, so R8 strips the
# whole package as unused, crashing the process the same way as above.
-keep class kotlin.jvm.internal.** { *; }
