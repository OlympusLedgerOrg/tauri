# GTK4 / WebKitGTK 6 Supply Chain Notes

Date: 2026-07-08

This note separates the Linux GTK4/WebKitGTK 6 runtime dependency changes from
standalone example dependencies and scanner noise. It is intended as a
review-facing artifact, not as a claim that the entire repository is currently
`cargo-vet` clean.

## Runtime Graph

Checked with:

```powershell
cargo metadata --format-version 1 --locked --all-features --filter-platform x86_64-unknown-linux-gnu
```

The Linux all-features graph contains the expected GTK4/WebKitGTK 6 crates:

| Package | Version | Repository | Notes |
|---------|---------|------------|-------|
| `gtk4` / `gdk4` / `gdk4-wayland` / `gdk4-x11` | `0.11.4` | `https://github.com/gtk-rs/gtk4-rs` | Active gtk-rs bindings for GTK4/GDK4. |
| `webkit6` | `0.6.1` | `https://gitlab.gnome.org/World/Rust/webkit6-rs` | WebKitGTK 6 binding; required for the GTK4 migration. |
| `javascriptcore6` | `0.6.0` | `https://gitlab.gnome.org/World/Rust/webkit6-rs` | Transitive WebKitGTK 6 binding. |
| `soup3` | `0.9.0` | `https://gitlab.gnome.org/World/Rust/soup3-rs` | Transitive GNOME/libsoup binding. |
| `ksni` | `0.2.2` | `https://github.com/iovxw/ksni` | Real optional Linux StatusNotifier tray backend. |
| `arc-swap` | `1.9.2` | `https://github.com/vorner/arc-swap` | Used by Linux menu/tray support. |
| `async-channel` | `2.5.0` | `https://github.com/smol-rs/async-channel` | Transitive async utility. |

`wayland-scanner` is pinned to Smithay commit
`d07c4f91f28b42e5a485823ffd9d8d5a210b1053`. The crates.io `0.31.10`
release requires vulnerable `quick-xml 0.39`, while that upstream commit moves
the unchanged scanner to `quick-xml 0.41` to resolve `RUSTSEC-2026-0194` and
`RUSTSEC-2026-0195`. Remove the pin after Smithay publishes a compatible
crates.io release containing the fix.

`ksni` is intentionally pulled through `tauri -> tray-icon/linux-ksni`.
Removing it means deliberately dropping or replacing that StatusNotifier tray
path, not merely trimming dead dependency weight.

`libadwaita` is not in the default root graph. It is an optional Tao feature:

```powershell
cargo tree --features libadwaita --target x86_64-unknown-linux-gnu -i libadwaita
```

## Example-Only Graphics Deps

The Socket table lists `wgpu`, `glow`, `pollster`, and `eframe`, but they are
not in the root Linux all-features graph.

Checked with:

```powershell
cargo tree --all-features --target x86_64-unknown-linux-gnu -i wgpu
cargo tree --all-features --target x86_64-unknown-linux-gnu -i eframe
```

Both commands report no package match from the root workspace.

Their actual locations are standalone vendored-port examples:

| Package | Location | Use |
|---------|----------|-----|
| `wgpu` | `ports/wry/Cargo.toml` dev-dependency | `ports/wry/examples/wgpu.rs` |
| `pollster` | `ports/wry/Cargo.toml` dev-dependency | `ports/wry/examples/wgpu.rs` |
| `glow` | `ports/wry/Cargo.toml` dev-dependency | `ports/wry/examples/gtk_opengl.rs` |
| `eframe` | `ports/tray-icon/Cargo.toml` dev-dependency | `ports/tray-icon/examples/egui.rs` |

The root workspace explicitly excludes `ports/muda`, `ports/tao`,
`ports/tray-icon`, and `ports/wry`, while patching crates.io to those local
paths. These example deps should not be treated as shipped Tauri runtime deps.

## Socket Warning Triage

Socket's "obfuscated code" warnings hit foundational ecosystem crates:

| Package | Root Graph Status | Triage |
|---------|-------------------|--------|
| `hyper-util` `0.1.20` | Present | Comes through networking/client/server tooling such as `reqwest`, `axum`, and `jsonrpsee`; Hyperium-maintained. |
| `tokio` `1.52.3` | Present | Core async runtime; Tokio-maintained. |
| `libc` `0.2.186` | Present | Rust-lang maintained low-level platform crate. |
| `zerocopy` `0.8.53` | Present | Google-maintained parsing/layout crate. |
| `openssl` `0.10.81` | Present only with all-features Linux resolution | Comes through `native-tls`, then `tauri-bundler` / `ureq`; absent from the default Linux tree. |

These warnings should be handled as scanner false-positive triage, not as a
reason to revert the GTK4/WebKitGTK 6 migration.

## cargo-vet Status

`cargo-vet` store version is `0.9`. On Windows with Rust `1.95`, installing
`cargo-vet 0.9.1` without its old lockfile fails in `flock.rs` because the
Windows handle type resolves to a different `c_void`.

Working bootstrap command:

```powershell
cargo +1.92 install cargo-vet --version 0.9.1 --locked --root .\.tmp-cargo-vet-0.9.1
$env:PATH = "$PWD\.tmp-cargo-vet-0.9.1\bin;$env:PATH"
cargo +1.92 vet --version
```

This branch now adds `audit-as-crates-io` policy entries for local patched
crates that match published crate versions:

```text
muda, tao, tao-macros, tauri-bundler, tauri-cli, tauri-driver,
tauri-macos-sign, tray-icon, wry
```

After formatting a disposable copy of the `supply-chain` store and running:

```powershell
cargo-vet.exe vet check --store-path <formatted-copy> --locked --output-format=json
```

the policy mismatch is gone, but the full graph still fails vetting with 985
missing criteria. The GTK/WebKit-related missing criteria are:

```text
arc-swap 1.9.2
async-channel 2.5.0
gdk4 0.11.4
gdk4-sys 0.11.4
gdk4-wayland 0.11.4
gdk4-wayland-sys 0.11.0
gdk4-x11 0.11.4
gdk4-x11-sys 0.11.0
gtk4 0.11.4
gtk4-macros 0.11.4
gtk4-sys 0.11.4
javascriptcore6 0.6.0
javascriptcore6-sys 0.6.0
ksni 0.2.2
soup3 0.9.0
soup3-sys 0.9.0
webkit6 0.6.1
webkit6-sys 0.6.0
```

Recommendation: do not mass-exempt the full 985-package graph in this GTK
migration patch. Split cargo-vet remediation into its own supply-chain PR or
audit packet. For this migration, document the GTK/WebKit-specific delta and
avoid conflating it with stale repo-wide vet coverage.

## Recommendation

Keep the GTK4/WebKitGTK 6 runtime dependencies and keep `ksni` if the
StatusNotifier tray path is in scope.

If the goal is to reduce scanner noise, remove or quarantine the standalone
vendored-port examples that pull `wgpu`, `glow`, `pollster`, and `eframe`.
That is a maintenance decision for the vendored ports, not a runtime security
fix for Tauri's Linux GTK4/WebKitGTK 6 path.
