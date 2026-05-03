package com.feelvision.domain.model

enum class PhysicalButton { A, B, C }

sealed class ButtonEvent {
    data class ShortPress(val button: PhysicalButton) : ButtonEvent()
    data class LongPress (val button: PhysicalButton) : ButtonEvent()
    data class DoubleTap (val button: PhysicalButton) : ButtonEvent()
    data class Hold5s    (val button: PhysicalButton) : ButtonEvent()
}