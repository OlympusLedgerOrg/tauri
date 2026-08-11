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
