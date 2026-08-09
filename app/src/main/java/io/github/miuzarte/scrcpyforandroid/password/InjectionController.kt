package io.github.miuzarte.scrcpyforandroid.password

import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InjectionController {
    suspend fun inject(password: CharArray) {
        try {
            withContext(Dispatchers.IO) {
                AppRuntime.scrcpy?.injectText(String(password))
            }
        } finally {
            password.fill('\u0000')
        }
    }
}
