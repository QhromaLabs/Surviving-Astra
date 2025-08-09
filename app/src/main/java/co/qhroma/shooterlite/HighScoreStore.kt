package co.qhroma.shooterlite

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the highest score using SharedPreferences.
 */
class HighScoreStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("scores", Context.MODE_PRIVATE)

    fun getBest(): Int = prefs.getInt(KEY, 0)

    fun submit(score: Int): Int {
        val best = getBest()
        if (score > best) {
            prefs.edit { putInt(KEY, score) }
            return score
        }
        return best
    }

    companion object { private const val KEY = "best_score" }
}
