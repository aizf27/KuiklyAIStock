package com.example.kuiklyaistock

import android.app.Application

class KRApplication : Application() {

    init {
        application = this
    }

    companion object {
        lateinit var application: Application
    }
}