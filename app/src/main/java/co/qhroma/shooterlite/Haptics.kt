package co.qhroma.shooterlite

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Small helper to perform a short vibration on hits.
 */
class Haptics(ctx: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        val vm = ctx.getSystemService(VibratorManager::class.java)
        vm?.defaultVibrator
    } else {
        ctx.getSystemService(Vibrator::class.java)
    }

    fun buzz(duration: Long = 15L) {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= 26) {
                it.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(duration)
            }
        }
    }
}
