package co.qhroma.shooterlite

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Tiny wrapper around [SoundPool] to handle shoot/pop effects.
 * If the raw resources are missing the calls fail silently.
 */
class AudioManager(context: Context) {
    private val pool: SoundPool
    private val shootId: Int
    private val popId: Int

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setAudioAttributes(attrs).setMaxStreams(2).build()

        shootId = loadIfExists(context, "shoot")
        popId = loadIfExists(context, "pop")
    }

    private fun loadIfExists(ctx: Context, name: String): Int {
        val resId = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        return if (resId != 0) pool.load(ctx, resId, 1) else 0
    }

    fun playShoot() { if (shootId != 0) pool.play(shootId, 1f, 1f, 0, 0, 1f) }
    fun playPop() { if (popId != 0) pool.play(popId, 1f, 1f, 0, 0, 1f) }
}
