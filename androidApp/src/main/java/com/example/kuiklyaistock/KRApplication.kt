package com.example.kuiklyaistock

import android.app.Application
import android.content.Context

class KRApplication : Application() {

    init {
        application = this
    }

    companion object {
        lateinit var application: Application

        @JvmStatic
        fun getAppContext(): Context = application.applicationContext
    }
}