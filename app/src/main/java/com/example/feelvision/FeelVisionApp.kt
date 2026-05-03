package com.feelvision

import android.app.Application
import com.feelvision.domain.button.ButtonHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FeelVisionApp : Application() {
    // Eagerly injected so the coroutine in ButtonHandler.init{} starts at launch
    @Inject lateinit var buttonHandler: ButtonHandler
}