/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.libremobileos.sidebar.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.android.wm.shell.bubbles.IBubbles
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.bubbles.logging.EntryPoint

object BubbleHelper {

    private const val TAG = "BubbleHelper"
    private const val BUBBLE_ACTION = "com.android.systemui.action.BUBBLE_LAUNCHER"
    private const val SYSUI_PACKAGE = "com.android.systemui"

    @Volatile private var sBubbles: IBubbles? = null

    fun isSupported(): Boolean = BubbleAnythingFlagHelper.enableCreateAnyBubble()

    fun bind(context: Context) {
        if (!isSupported()) return
        val intent = Intent(BUBBLE_ACTION).setPackage(SYSUI_PACKAGE)
        context.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                sBubbles = IBubbles.Stub.asInterface(service)
                Log.d(TAG, "BubbleLauncherService connected")
            }
            override fun onServiceDisconnected(name: ComponentName) {
                sBubbles = null
                Log.d(TAG, "BubbleLauncherService disconnected")
            }
        }, Context.BIND_AUTO_CREATE)
    }

    fun launchAsBubble(packageName: String, activityName: String) {
        val bubbles = sBubbles ?: run {
            Log.e(TAG, "Bubble service not connected – call bind() first")
            return
        }
        val intent = Intent().apply {
            setClassName(packageName, activityName)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        bubbles.showAppBubble(intent, Process.myUserHandle(), EntryPoint.LAUNCHER_ICON_MENU, null)
    }
}
