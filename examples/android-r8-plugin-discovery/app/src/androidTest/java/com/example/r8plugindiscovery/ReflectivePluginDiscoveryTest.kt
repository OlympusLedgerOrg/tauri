// Copyright 2019-2024 Tauri Programme within The Commons Conservancy
// SPDX-License-Identifier: Apache-2.0
// SPDX-License-Identifier: MIT

package com.example.r8plugindiscovery

import androidx.activity.result.ActivityResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.PermissionCallback
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin
import com.example.r8plugindiscovery.plugin.ACTIVITY_CALLBACK_RESULT
import com.example.r8plugindiscovery.plugin.DiscoveryDefaultsPlugin
import com.example.r8plugindiscovery.plugin.DiscoveryTestPlugin
import com.example.r8plugindiscovery.plugin.LOCATION_PERMISSION
import com.example.r8plugindiscovery.plugin.PERMISSION_CALLBACK_RESULT
import com.example.r8plugindiscovery.plugin.PING_RESULT
import com.fasterxml.jackson.databind.ObjectMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method

/**
 * Regression test for crates/tauri/mobile/android/consumer-rules.pro
 * (CodeRabbit finding on PR #17: R8 can strip `RuntimeVisibleAnnotations` and
 * `AnnotationDefault`, which breaks reflective plugin discovery even though
 * the classes/methods themselves survive the `-keep` rules).
 *
 * This is an `androidTest`, not a JVM unit test, and the module's
 * `testBuildType` is pinned to `"release"` (app/build.gradle.kts): unit tests
 * run straight from compiled Kotlin and never see R8 output, so they would
 * pass identically whether or not the keep rule exists. Only an instrumented
 * test that installs and runs the actual minified "release" APK exercises the
 * bug this guards against.
 *
 * Every reflective call below is copy-matched to
 * `app.tauri.plugin.PluginHandle` (`init` / `indexMethods()` / `invoke()`) so
 * this test fails exactly when real plugin loading would fail, not on some
 * looser approximation of it.
 */
@RunWith(AndroidJUnit4::class)
class ReflectivePluginDiscoveryTest {

    @Test
    fun tauriPluginAnnotationAndPermissionsSurviveMinification() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val instance = DiscoveryTestPlugin(activity)

            // Mirrors PluginHandle.init: `instance.javaClass.getAnnotation(TauriPlugin::class.java)`
            val annotation = instance.javaClass.getAnnotation(TauriPlugin::class.java)
            assertNotNull(
                "@TauriPlugin annotation was stripped by R8 -- RuntimeVisibleAnnotations was not kept",
                annotation
            )

            val permissions = annotation!!.permissions
            assertEquals(1, permissions.size)
            assertEquals(LOCATION_PERMISSION, permissions[0].strings[0])
            // `alias` was never set on this @Permission -- reading it exercises
            // AnnotationDefault for a nested annotation member.
            assertEquals(
                "Permission.alias default value was lost -- AnnotationDefault was not kept",
                "",
                permissions[0].alias
            )
        }
        scenario.close()
    }

    @Test
    fun omittedTauriPluginPermissionsDefaultSurvivesMinification() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val instance = DiscoveryDefaultsPlugin(activity)
            val annotation = instance.javaClass.getAnnotation(TauriPlugin::class.java)
            assertNotNull(annotation)

            // `@TauriPlugin` was used with no `permissions = [...]` at all, so this
            // reads the annotation *interface's* AnnotationDefault attribute for
            // `permissions`, not a value baked into the call site. Without
            // AnnotationDefault this throws IncompleteAnnotationException instead
            // of returning the declared default ([]).
            val permissions = annotation!!.permissions
            assertTrue(
                "TauriPlugin.permissions default value was lost -- AnnotationDefault was not kept",
                permissions.isEmpty()
            )
        }
        scenario.close()
    }

    @Test
    fun commandMethodIsDiscoverableAndInvokable() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val instance = DiscoveryTestPlugin(activity)

            val method = findMethodOrFail(instance, Command::class.java)
            val response = invokeAndCapture(instance, method, argsJson = "{}")

            assertEquals(PING_RESULT, JSONObject(response).getString("value"))
        }
        scenario.close()
    }

    @Test
    fun permissionCallbackMethodIsDiscoverableAndInvokable() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val instance = DiscoveryTestPlugin(activity)

            val method = findMethodOrFail(instance, PermissionCallback::class.java)
            val response = invokeAndCapture(instance, method, argsJson = "{}")

            assertEquals(PERMISSION_CALLBACK_RESULT, JSONObject(response).getString("value"))
        }
        scenario.close()
    }

    @Test
    fun activityCallbackMethodIsDiscoverableAndInvokable() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val instance = DiscoveryTestPlugin(activity)

            val method = findMethodOrFail(instance, ActivityCallback::class.java)

            var captured: String? = null
            val invoke = newInvoke("{}") { captured = it }
            method.isAccessible = true
            // ActivityCallback methods take (Invoke, ActivityResult) -- see
            // PluginHandle.startActivityForResult.
            method.invoke(instance, invoke, ActivityResult(7, null))

            assertEquals(ACTIVITY_CALLBACK_RESULT, JSONObject(captured!!).getString("value"))
            assertEquals(7, JSONObject(captured!!).getInt("resultCode"))
        }
        scenario.close()
    }

    /**
     * Mirrors `PluginHandle.indexMethods()`: walk `declaredMethods` and select
     * by `isAnnotationPresent`. Fails the test (not silently returns null) if
     * the annotation attribute was stripped, since that's the actual failure
     * mode a real app hits.
     */
    private fun findMethodOrFail(
        instance: Plugin,
        annotationClass: Class<out Annotation>
    ): Method {
        val found = instance.javaClass.declaredMethods.firstOrNull {
            it.isAnnotationPresent(annotationClass)
        }
        assertNotNull(
            "No method annotated @${annotationClass.simpleName} was discoverable on " +
                "${instance.javaClass.name} -- RuntimeVisibleAnnotations was not kept",
            found
        )
        return found!!
    }

    /** Mirrors `PluginHandle.invoke()`: `method.isAccessible = true; method(instance, invoke)`. */
    private fun invokeAndCapture(instance: Plugin, method: Method, argsJson: String): String {
        var captured: String? = null
        val invoke = newInvoke(argsJson) { captured = it }
        method.isAccessible = true
        method.invoke(instance, invoke)
        assertNotNull("Method ${method.name} did not resolve/reject the Invoke", captured)
        return captured!!
    }

    private fun newInvoke(argsJson: String, onResponse: (String) -> Unit): Invoke {
        val resolveCallbackId = 1L
        return Invoke(
            id = 0L,
            command = "test",
            callback = resolveCallbackId,
            error = 2L,
            sendResponse = { callbackId, data ->
                if (callbackId == resolveCallbackId) onResponse(data)
            },
            argsJson = argsJson,
            jsonMapper = ObjectMapper()
        )
    }
}
