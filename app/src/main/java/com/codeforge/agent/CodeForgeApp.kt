package com.codeforge.agent

import android.app.Application

class CodeForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: CodeForgeApp
            private set
    }
}