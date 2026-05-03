package com.feelvision.domain.model

sealed class CapturePolicy {
    data object SingleShot : CapturePolicy()
    data class  BurstInterval(val count: Int, val intervalMs: Long) : CapturePolicy()
    data class  Continuous   (val intervalMs: Long) : CapturePolicy()
    data object None : CapturePolicy()
}