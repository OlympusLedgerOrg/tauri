// Copyright 2019-2024 Tauri Programme within The Commons Conservancy
// SPDX-License-Identifier: Apache-2.0
// SPDX-License-Identifier: MIT

package com.example.r8plugindiscovery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * A live [android.app.Activity] for the androidTest to instantiate
 * [com.example.r8plugindiscovery.plugin.DiscoveryTestPlugin] against,
 * mirroring how a real Tauri plugin is constructed with `Plugin(activity)`.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
