package co.qhroma.shooterlite.models

import android.graphics.Bitmap

/**
 * Slow moving target drifting toward the player. Now supports rotation and a sprite bitmap.
 */
data class Target(
    val pos: Vec2 = Vec2(),
    val vel: Vec2 = Vec2(),
    var radius: Float = 0f,
    var alive: Boolean = true,
    // Index into asteroid sprite list
    var type: Int = 0,
    // Current rotation in degrees and spin speed (deg/sec)
    var angle: Float = 0f,
    var spin: Float = 0f,
    // Pre-scaled bitmap to render for this target (set at spawn)
    var bmp: Bitmap? = null
)
