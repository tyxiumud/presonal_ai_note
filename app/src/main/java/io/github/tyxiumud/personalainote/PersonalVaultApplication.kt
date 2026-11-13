package io.github.tyxiumud.personalainote

import android.app.Application

class PersonalVaultApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}
