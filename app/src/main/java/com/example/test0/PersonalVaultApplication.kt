package com.example.test0

import android.app.Application

class PersonalVaultApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}
