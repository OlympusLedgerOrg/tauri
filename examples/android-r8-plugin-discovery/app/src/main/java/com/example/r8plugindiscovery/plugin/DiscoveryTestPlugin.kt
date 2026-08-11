// Copyright 2019-2024 Tauri Programme within The Commons Conservancy
// SPDX-License-Identifier: Apache-2.0
// SPDX-License-Identifier: MIT

package com.example.r8plugindiscovery.plugin

import android.app.Activity
import androidx.activity.result.ActivityResult
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.Permission
import app.tauri.annotation.PermissionCallback
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin

// Markers the androidTest asserts on. If these constants disappeared from the
// resolved output (renamed, inlined wrong, etc.) the test would fail loudly
// rather than silently pass on a no-op.
const val PING_RESULT = "pong-from-r8-minified-plugin"
const val ACTIVITY_CALLBACK_RESULT = "activity-callback-ran"
const val PERMISSION_CALLBACK_RESULT = "permission-callback-ran"
const val LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"

/**
 * Exercises every reflective-discovery surface `consumer-rules.pro` keeps for
 * a Tauri plugin:
 *  - `@TauriPlugin` with an explicit `permissions` array (nested `@Permission`
 *    annotations), read via `instance.javaClass.getAnnotation(TauriPlugin::class.java)`
 *    exactly like `PluginHandle.init`.
 *  - `@Command`, `@ActivityCallback`, `@PermissionCallback` methods, discovered
 *    via `Method.isAnnotationPresent` / `getAnnotation` exactly like
 *    `PluginHandle.indexMethods()`, and then invoked via `Method.invoke`
 *    exactly like `PluginHandle.invoke()`.
 *
 * One `@Permission` below omits `alias`, deliberately relying on its default
 * value (`""`) so the test also covers `AnnotationDefault`, not just
 * `RuntimeVisibleAnnotations`.
 */
@TauriPlugin(
    permissions = [
        Permission(strings = [LOCATION_PERMISSION])
    ]
)
class DiscoveryTestPlugin(activity: Activity) : Plugin(activity) {

    @Command
    fun ping(invoke: Invoke) {
        val ret = JSObject()
        ret.put("value", PING_RESULT)
        invoke.resolve(ret)
    }

    @ActivityCallback
    fun onPickResult(invoke: Invoke, result: ActivityResult) {
        val ret = JSObject()
        ret.put("value", ACTIVITY_CALLBACK_RESULT)
        ret.put("resultCode", result.resultCode)
        invoke.resolve(ret)
    }

    @PermissionCallback
    fun locationPermissionCallback(invoke: Invoke) {
        val ret = JSObject()
        ret.put("value", PERMISSION_CALLBACK_RESULT)
        invoke.resolve(ret)
    }
}

/**
 * A second plugin that declares no `permissions` at all, so reading
 * `TauriPlugin.permissions` on it exercises the *default* value (`[]`) of the
 * annotation member itself -- the other half of what `AnnotationDefault`
 * covers, distinct from the nested `@Permission.alias` default above.
 * Without `-keepattributes ...,AnnotationDefault`, accessing an omitted
 * annotation member throws `IncompleteAnnotationException` at runtime instead
 * of returning the default.
 */
@TauriPlugin
class DiscoveryDefaultsPlugin(activity: Activity) : Plugin(activity) {
    @Command
    fun ping(invoke: Invoke) {
        invoke.resolve(JSObject())
    }
}
