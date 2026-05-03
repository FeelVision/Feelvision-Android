package com.feelvision.inference

sealed class InferenceResult {
    data class  Success  (val text: String) : InferenceResult()
    data class  Streaming(val partial: String) : InferenceResult()
    data class  Failure  (val error: String, val cause: Throwable? = null) : InferenceResult()
    data object NotReady : InferenceResult()
}