---
"tauri": minor:breaking
"tauri-runtime": minor:breaking
---

Require webview navigation, IPC, page-load, and document-title callbacks to be `Sync` in addition to `Send`, matching the thread-safety contract of the vendored Wry runtime.
