---
tauri: patch
---

On Android, keep `RuntimeVisibleAnnotations` and `AnnotationDefault` in the `tauri-android` consumer ProGuard/R8 rules. Without them, R8 could strip the annotation metadata that reflective plugin discovery relies on (`TauriPlugin.permissions`, `@Command`/`@ActivityCallback`/`@PermissionCallback` method lookup) in a minified release build, even though the class and method `-keep` rules on their own left the plugin's classes and methods in place. Added `examples/android-r8-plugin-discovery`, an instrumented-test module that builds a minified "release" app depending only on the shipped consumer rules and asserts plugin discovery still works.
