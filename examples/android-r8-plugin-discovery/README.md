# android-r8-plugin-discovery

Regression test for `crates/tauri/mobile/android/consumer-rules.pro`.

## What this is testing

Tauri's Android plugin dispatch (`app.tauri.plugin.PluginHandle`) is entirely
reflection-based: it reads the `@TauriPlugin` annotation (including its
`permissions` array) off a plugin instance's class, and finds `@Command` /
`@ActivityCallback` / `@PermissionCallback` methods via
`Method.isAnnotationPresent` / `getAnnotation`. R8, by default, strips the
`RuntimeVisibleAnnotations` and `AnnotationDefault` class-file attributes that
this depends on, even when `-keep` rules elsewhere preserve the classes and
methods themselves untouched and unrenamed. The result is a plugin that looks
intact after minification but silently fails to be discovered or dispatches
to the wrong (or no) method at runtime.

`crates/tauri/mobile/android/consumer-rules.pro` ships
`-keepattributes RuntimeVisibleAnnotations,AnnotationDefault` specifically to
prevent this. This module proves that rule is present and sufficient, using a
real R8-minified build rather than a source-level assertion.

## Why this is a separate Gradle project

This is a standalone Gradle project, not something scaffolded by
`cargo tauri android init` — it doesn't need the Rust/NDK toolchain at all,
only the Android SDK. `settings.gradle.kts` includes `:tauri-android` by
pointing directly at `../../crates/tauri/mobile/android`, the same way
`crates/tauri-build/src/mobile.rs::generate_gradle_files` wires a real Tauri
app's `tauri.settings.gradle` — so the `:app` module receives
`consumer-rules.pro` exactly the way a real consuming app would (via
`consumerProguardFiles`), not a copy of it.

## Why this must run on a device/emulator, not as a unit test

`app/build.gradle.kts` sets `isMinifyEnabled = true` and
`testBuildType = "release"` for the `release` build type (signed with the
debug key purely so the test APK can be installed — this app is never
distributed). The test in `app/src/androidTest/.../ReflectivePluginDiscoveryTest.kt`
is an instrumented test, deliberately _not_ a JVM unit test under
`app/src/test`: AGP unit tests compile and run straight from Kotlin sources
and never pass through R8, so a unit test making the same assertions would
pass identically whether or not `consumer-rules.pro` had the keep rule at
all, proving nothing. Only `connectedReleaseAndroidTest` installs and
exercises the actual minified APK.

`app/proguard-rules.pro`, the app module's own (non-consumer) ProGuard file,
deliberately adds no keep rule of its own for the sample plugin. If it needed
one for the test to pass, that would mean the library's consumer rule is not
actually sufficient on its own — the exact regression this module exists to
catch.

The app and sample plugin live under `com.example.r8plugindiscovery`, not
`app.tauri.*`. `consumer-rules.pro`'s first rule
(`-keep class app.tauri.** { ... }`) keeps every class under that package
unrenamed regardless of the `AnnotationDefault`/`RuntimeVisibleAnnotations`
fix, so putting the test app under `app.tauri.*` would have made it
ambiguous whether a passing test was actually exercising the keep rule under
test or just riding along on that unrelated wildcard.

## Running locally

Requires an Android SDK and a running device or emulator (`adb devices` shows
at least one).

```sh
./gradlew :app:connectedReleaseAndroidTest
```

## CI

`.github/workflows/test-android-r8-plugin-discovery.yml` runs this on a
GitHub-hosted emulator via `reactivecircus/android-emulator-runner`, gated on
changes to `consumer-rules.pro`, the plugin annotation/dispatch sources, and
this directory.

> **Note (authoring provenance):** this module was authored and reviewed for
> correctness against `PluginHandle`'s actual reflection calls, but has not
> been executed locally — the environment it was written in had no Android
> SDK, Gradle, or JDK 17+ available. The CI workflow above is the first real
> execution of this build. If it fails, the most likely culprits are Gradle
> module wiring (`settings.gradle.kts` / `build.gradle.kts`) rather than the
> underlying `consumer-rules.pro` fix, which mirrors documented R8/ProGuard
> behavior.
