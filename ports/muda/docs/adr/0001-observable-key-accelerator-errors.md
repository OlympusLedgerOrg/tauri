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

Deferring backend validation can also make it harder to identify the failing
child when an operation materializes several items. The existing transparent
accelerator error does not preserve that item identity. This ADR therefore
includes both per-item and aggregate application errors.

## Decision

Keep the existing `build() -> Item` signatures. `build()` must not call
`set_key_accelerator()`. Instead, it constructs each item with its
`KeyAccelerator` stored, using crate-private construction helpers where needed.
Construction neither converts the accelerator for a native backend nor installs
it. The backend validates and applies it when materializing the item for a menu,
once per native representation; it is not first applied during `build()` and
then applied again during attachment.

The error boundary is the first fallible operation that materializes the native
representation. On Windows and macOS this is normally `Menu::append`,
`Menu::insert`, or the corresponding `Submenu` operation. On GTK, adding an item
before a menu instance exists only records the child, so validation or
installation may instead occur during `init_for_gtk_window`; adding to an
already initialized menu performs it during the add operation.

Backend representability or application failures must be reported as:

```rust
Error::AcceleratorApplicationFailed {
    item_id: MenuId,
    accelerator: KeyAccelerator,
}
```

The variant carries the exact attempted item and accelerator. Syntax and
conversion failures raised while configuring a builder remain
`AcceleratorParseError`; failures showing that an already parsed accelerator
cannot be applied by the target backend use the new variant.

Batch operations use a typed payload and aggregate variant:

```rust
pub struct AcceleratorApplicationFailure {
    pub item_id: MenuId,
    pub accelerator: KeyAccelerator,
}

Error::AcceleratorBatchApplicationFailed {
    failures: Vec<AcceleratorApplicationFailure>,
}
```

`Menu::append_items`, `Menu::insert_items`, `Menu::prepend_items`, their
`Submenu` equivalents, and GTK initialization prevalidate every item they would
materialize. If any validation fails, they return a non-empty `failures` list in
input order, using depth-first child order for a recorded menu tree, and perform
no materialization. A batch API uses the aggregate variant even when only one
item fails; single-item operations and `try_build()` use
`AcceleratorApplicationFailed`.

When GTK initialization materializes multiple recorded children, it must
prevalidate every configured accelerator before materializing any child. It
collects every failure in deterministic traversal order and leaves the batch
unmaterialized when the collection is non-empty.

Add `try_build() -> crate::Result<Item>` consistently to all three builders as
an opt-in early check. It performs side-effect-free validation that the
configured `KeyAccelerator` is representable by the target backend, then uses
the same stored construction path as `build()`. It is a validation pass, not a
trial installation into a temporary native menu, so menu-context failures can
still occur later. It must resolve the item's concrete ID before validation so
an `AcceleratorApplicationFailed` error identifies the attempted item.

The builder's `id: Option<MenuId>` represents only whether the caller supplied
an explicit ID. Construction always assigns a concrete `MenuId` when none was
provided. This decision introduces no optional item ID; any future diagnostic
that identifies a constructed item, including `AcceleratorApplicationFailed`,
uses `MenuId`, not `Option<String>`.

Do not panic, silently remove the configured accelerator, or change only the
icon-item builder. Reserve any change to the existing `build()` return type for
a major release. Attachment must validate before mutating parent state, or roll
back that state if native materialization fails.

## Consequences

- Existing callers keep their current source-compatible builder API.
- A configured `KeyAccelerator` is retained instead of being silently dropped.
- `build()` performs no native accelerator work; `try_build()` provides an
  additive early representability check for callers that require one.
- Platform-specific failures surface through an existing `Result` boundary,
  potentially later when the item is attached or the menu is initialized.
- An item that is built but never attached or materialized never surfaces a
  deferred backend error. This is accepted because it never acquires a native
  accelerator; callers that need eager validation can use `try_build()`.
- `try_build()` cannot guarantee that later menu-context installation succeeds.
- Representability and application failures identify the exact `MenuId` and
  `KeyAccelerator`. The additive variant is compatible with the existing
  non-exhaustive `Error` enum.
- Batch operations report every accelerator failure through a typed, ordered
  aggregate payload instead of forcing callers to fix one item per attempt.
- GTK accelerator validation during initialization is collecting and batch-atomic.
- Every built item has a concrete `MenuId`.
- The implementation and tests must cover all three builders together.

## Rejected alternatives

- Returning `Result` only from `IconMenuItemBuilder::build()`: inconsistent and
  unnecessarily breaking.
- Calling `expect` or otherwise panicking from `build()`: turns a recoverable
  platform error into a process failure.
- Continuing to discard `set_key_accelerator()` errors: silently loses the
  caller's configured behavior.
- Returning only the underlying `AcceleratorParseError` from attachment:
  preserves the cause category but loses the identity of the failing item.
- Returning only the first accelerator error from a batch: hides other invalid
  items and requires repeated materialization attempts to discover them.
- Calling `set_key_accelerator()` during `build()` and applying it again during
  attachment: duplicates backend work and retains the swallowed-error problem.
- Having `try_build()` install into a temporary native menu: introduces native
  side effects and still cannot prove that installation in the real menu will
  succeed.

## Validation

The follow-up implementation must verify that each builder retains its key
accelerator without invoking backend installation during `build()`, and that it
is applied once per native representation during attachment or initialization.
`try_build()` must accept and reject representative supported and unsupported
keys without creating a native menu, and its error must contain the resolved
`MenuId` and configured `KeyAccelerator`. A failed materialization must not leave
the item partially attached. A GTK initialization test with an invalid
accelerator among multiple recorded children must verify that none of the batch
is materialized and that `AcceleratorBatchApplicationFailed` contains every
invalid child in deterministic order. Batch APIs must also be tested with one
failure to preserve their aggregate return shape. Tests must cover automatic
`MenuId` assignment and compile existing `build()` call sites without changes.
