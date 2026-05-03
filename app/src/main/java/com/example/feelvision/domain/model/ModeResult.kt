package com.feelvision.domain.model

sealed class ModeResult {
    data class  TextRead           (val text: String, val confidence: Float = 1f) : ModeResult()
    data class  PersonRecognized   (val name: String, val relation: String, val confidence: Float) : ModeResult()
    data class  CurrencyDetected   (val denomination: String, val series: String, val confidence: Float) : ModeResult()
    data class  NavigationInstruction(val instruction: String, val distanceM: Float) : ModeResult()
    data class  NarrationText      (val description: String) : ModeResult()
    data class  EduContent         (val content: String) : ModeResult()
    data class  UnknownPerson      (val confidence: Float) : ModeResult()
    data object NoResult : ModeResult()
    data class  Error              (val message: String) : ModeResult()
}