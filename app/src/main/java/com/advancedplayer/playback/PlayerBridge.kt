package com.advancedplayer.playback

import android.os.SystemClock
import com.advancedplayer.playback.dsp.FxParams
import kotlinx.coroutines.flow.MutableStateFlow

data class SleepTimer(val endAtElapsed: Long? = null, val endOfTrack: Boolean = false) {
    val active get() = endAtElapsed != null || endOfTrack
    fun remainingMs(): Long? = endAtElapsed?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
}

data class AbLoop(val trackId: Long? = null, val a: Long? = null, val b: Long? = null) {
    val active get() = a != null && b != null
}

/**
 * State shared between the UI and [PlaybackService]. Both live in the same process, so
 * plain flows are simpler than custom session commands for these app-only features.
 */
object PlayerBridge {
    val fx = FxParams()
    val sleepTimer = MutableStateFlow(SleepTimer())
    val abLoop = MutableStateFlow(AbLoop())

    fun startSleepTimer(minutes: Int) {
        sleepTimer.value = SleepTimer(endAtElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    fun sleepAtEndOfTrack() {
        sleepTimer.value = SleepTimer(endOfTrack = true)
    }

    fun cancelSleepTimer() {
        sleepTimer.value = SleepTimer()
    }
}
