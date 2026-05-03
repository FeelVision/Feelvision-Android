package com.feelvision.hardware

import com.feelvision.domain.model.ButtonEvent
import com.feelvision.domain.model.PhysicalButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtonEventSource @Inject constructor() {

    private val _events = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ButtonEvent> = _events

    private val pressStartTimes = mutableMapOf<Int, Long>()
    private val LONG_PRESS_MS   = 600L

    fun onKeyDown(keyCode: Int): Boolean {
        pressStartTimes[keyCode] = System.currentTimeMillis()
        return keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
               keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
    }

    suspend fun onKeyUp(keyCode: Int): Boolean {
        val start  = pressStartTimes.remove(keyCode) ?: return false
        val held   = System.currentTimeMillis() - start
        val button = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP   -> PhysicalButton.A
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> PhysicalButton.B
            else -> return false
        }
        _events.emit(
            if (held >= LONG_PRESS_MS) ButtonEvent.LongPress(button)
            else                       ButtonEvent.ShortPress(button)
        )
        return true
    }
}