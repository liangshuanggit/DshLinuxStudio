package com.mobilelinuxstudio.app.runtime

import android.content.Context

object RuntimeProvider {
    @Volatile
    private var instance: LinuxRuntime? = null

    fun get(context: Context): LinuxRuntime {
        return instance ?: synchronized(this) {
            instance ?: LinuxRuntime(context.applicationContext).also { instance = it }
        }
    }
}
