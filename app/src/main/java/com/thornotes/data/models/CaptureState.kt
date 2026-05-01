package com.thornotes.data.models

sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data object Processing : CaptureState()
    data class Error(val message: String) : CaptureState()
}
