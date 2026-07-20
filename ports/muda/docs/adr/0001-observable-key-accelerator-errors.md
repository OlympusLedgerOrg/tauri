# ADR 0001: Make key accelerator errors observable without breaking builders

- Status: Accepted
- Date: 2026-07-20
- Scope: `MenuItemBuilder`, `CheckMenuItemBuilder`, and `IconMenuItemBuilder`

## Context

The three menu-item builders accept both `Accelerator` and `KeyAccelerator`.
Conversion and parsing failures are returned while configuring a builder, but
each infallible `build()` method currently applies the stored key accelerator
through the fallible `set_key_accelerator()` API and discards its result.

Changing only `IconMenuItemBuilder::build()` to return `Result` would leave the
builders inconsistent. Changing all three methods would make platform failures
observable immediately, but it would also be a breaking change to the public
builder API.

## Decision

Keep the existing `build() -> Item` signatures. Address the discarded-result
behavior consistently across all three builders in a dedicated Muda change by
constructing each item with its `KeyAccelerator` already stored, using
crate-private construction helpers where needed. Platform validation or
installation failures will then be returned by the existing fallible menu
attachment operations, such as `Menu::append` and `Menu::insert`.

Do not panic, silently remove the configured accelerator, or change only the
icon-item builder. If a platform must validate an accelerator before menu
attachment, introduce an additive `try_build()` API for all three builders and
reserve any change to the existing `build()` return type for a major release.

## Consequences

- Existing callers keep their current source-compatible builder API.
- A configured `KeyAccelerator` is retained instead of being silently dropped.
- Platform-specific failures surface through an existing `Result` boundary,
  although potentially later when the item is attached to a menu.
- The implementation and tests must cover all three builders together.

## Rejected alternatives

- Returning `Result` only from `IconMenuItemBuilder::build()`: inconsistent and
  unnecessarily breaking.
- Calling `expect` or otherwise panicking from `build()`: turns a recoverable
  platform error into a process failure.
- Continuing to discard `set_key_accelerator()` errors: silently loses the
  caller's configured behavior.

## Validation

The follow-up implementation must verify that each builder retains its key
accelerator and that unsupported platform accelerators are reported by a
fallible operation. It must also compile existing `build()` call sites without
changes.
