package com.libremobileos.sidebar.app

import android.app.Application
import android.os.Build

import com.libremobileos.sidebar.utils.BubbleHelper

/**
 * @author KindBrave
 * @since 2023/9/25
 */
class SidebarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BubbleHelper.bind(this)
    }

    companion object {
        const val CONFIG = "config"
    }
}
